package com.example.tasks.retirement;

import com.example.tasks.ingest.PublicHttp;
import com.example.tasks.warehouse.Warehouse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Locates an address with CartoCiudad (IGN), finds the INE census section that contains it
 * and returns its Habits household typologies. The address is neither stored nor logged.
 */
@Service
public class NeighbourhoodService {

	static final String GEOCODER = "https://www.cartociudad.es/geocoder/api/geocoder/findJsonp?q=";

	static final int YEAR = 2011;

	record Location(double lat, double lng, String label, String municipality, String province, String postalCode) {
	}

	private final PublicHttp http;

	private final Warehouse warehouse;

	private final ObjectMapper mapper;

	public NeighbourhoodService(PublicHttp http, Warehouse warehouse, ObjectMapper mapper) {
		this.http = http;
		this.warehouse = warehouse;
		this.mapper = mapper;
	}

	public Neighbourhood find(String address) {
		if (!warehouse.tableExists("census_sections") || !warehouse.tableExists("habits_raw")) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Faltan los datos de Habits o de secciones censales");
		}
		Location location = geocode(address);
		warehouse.loadSpatial();
		List<String[]> sections = warehouse.query("""
				SELECT cusec, municipality, cpro FROM census_sections
				WHERE ST_Contains(geom, ST_Point(?, ?)) LIMIT 1""",
				rs -> new String[] { rs.getString(1), rs.getString(2), rs.getString(3) }, location.lng(), location.lat());
		if (sections.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"La dirección no cae dentro de ninguna sección censal de 2011");
		}
		String cusec = sections.getFirst()[0];
		int province = Integer.parseInt(sections.getFirst()[2]);
		boolean catalog = warehouse.tableExists("habits_typologies");
		List<Neighbourhood.TypologyShare> typologies = warehouse.query("""
				WITH h AS (
					SELECT CO_TIPOL AS code,
					       sum(NU_FAMILIAS) FILTER (WHERE CO_HASECCION = ?) AS section,
					       sum(NU_FAMILIAS) FILTER (WHERE CO_PROV = ?) AS province,
					       sum(NU_FAMILIAS) AS national
					FROM habits_raw WHERE NU_ANO = ? AND CO_TIPOL <> 'Z' GROUP BY 1
				)
				SELECT h.code, %s, coalesce(h.section, 0),
				       100 * coalesce(h.section, 0) / nullif(sum(h.section) OVER (), 0),
				       100 * h.province / nullif(sum(h.province) OVER (), 0),
				       100 * h.national / sum(h.national) OVER ()
				FROM h %s
				ORDER BY 4 DESC NULLS LAST, h.code""".formatted(catalog ? "t.name, t.group_name" : "NULL, NULL",
				catalog ? "LEFT JOIN habits_typologies t USING (code)" : ""),
				rs -> new Neighbourhood.TypologyShare(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4),
						round(rs.getDouble(5)), round(rs.getDouble(6)), round(rs.getDouble(7))),
				cusec, province, YEAR);
		long families = typologies.stream().mapToLong(Neighbourhood.TypologyShare::families).sum();
		if (families == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"Habits no tiene datos de %d para la sección %s".formatted(YEAR, cusec));
		}
		return new Neighbourhood(location.label(), location.municipality(), location.province(),
				location.postalCode(), cusec, YEAR, families, typologies);
	}

	Location geocode(String address) {
		String body;
		try {
			body = http.getString(GEOCODER + URLEncoder.encode(address, StandardCharsets.UTF_8));
		}
		catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El geocodificador de CartoCiudad no responde");
		}
		Location location = parse(mapper, body);
		if (location == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"No encontramos esa dirección; prueba con calle, número y municipio");
		}
		return location;
	}

	/** Parses CartoCiudad's JSONP answer; null when there is no match. */
	static Location parse(ObjectMapper mapper, String jsonp) {
		int start = jsonp.indexOf('(');
		int end = jsonp.lastIndexOf(')');
		if (start < 0 || end <= start) {
			return null;
		}
		JsonNode n = mapper.readTree(jsonp.substring(start + 1, end));
		if (n == null || !n.path("lat").isNumber() || !n.path("lng").isNumber()) {
			return null;
		}
		String street = (n.path("tip_via").asString("") + " " + n.path("address").asString("")).strip();
		String number = n.path("portalNumber").isNumber() && n.path("portalNumber").asInt() > 0
				? " " + n.path("portalNumber").asInt() : "";
		return new Location(n.path("lat").asDouble(), n.path("lng").asDouble(), street + number,
				n.path("muni").asString(null), n.path("province").asString(null), n.path("postalCode").asString(null));
	}

	private static double round(double v) {
		return Math.round(v * 10) / 10.0;
	}

}
