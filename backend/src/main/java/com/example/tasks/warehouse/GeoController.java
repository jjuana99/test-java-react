package com.example.tasks.warehouse;

import com.example.tasks.ingest.connectors.HabitsConnector;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Census-section choropleth data: INE boundaries joined with Habits, rendered as GeoJSON in DuckDB. */
@RestController
@RequestMapping("/api/geo")
public class GeoController {

	public enum Metric {

		/** Families in the section. */
		FAMILIES("h.families"),
		/** Families per km². */
		DENSITY("h.families / nullif(s.km2, 0)"),
		/** Share (%) of one household typology over the section total. */
		SHARE("100 * h.typology_families / nullif(h.families, 0)");

		private final String sql;

		Metric(String sql) {
			this.sql = sql;
		}

	}

	public record Province(int code, String name) {
	}

	private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

	private final Warehouse warehouse;

	public GeoController(Warehouse warehouse) {
		this.warehouse = warehouse;
	}

	@GetMapping("/provinces")
	public List<Province> provinces() {
		return HabitsConnector.PROVINCES.entrySet().stream().map(e -> new Province(e.getKey(), e.getValue())).toList();
	}

	@GetMapping("/sections")
	public ResponseEntity<String> sections(@RequestParam int province, @RequestParam(defaultValue = "2011") int year,
			@RequestParam(defaultValue = "FAMILIES") Metric metric, @RequestParam(defaultValue = "A") String typology) {
		if (!typology.matches("[A-Z]")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipología no válida: " + typology);
		}
		if (!warehouse.tableExists("census_sections")) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Importa la fuente 'INE · Secciones censales 2011' desde el Resumen");
		}
		if (!warehouse.tableExists("habits_raw")) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Importa la fuente Habits desde el Resumen");
		}
		warehouse.loadSpatial();
		// metric.sql comes from the enum, never from the request; everything else is bound
		String geoJson = warehouse.query("""
				WITH h AS (
					SELECT CO_HASECCION AS cusec,
					       sum(NU_FAMILIAS) FILTER (WHERE CO_TIPOL = 'Z') AS families,
					       sum(NU_FAMILIAS) FILTER (WHERE CO_TIPOL = ?) AS typology_families
					FROM habits_raw
					WHERE NU_ANO = ? AND CO_PROV = ?
					GROUP BY CO_HASECCION
				)
				SELECT json_object(
					'type', 'FeatureCollection',
					'features', coalesce(list(json_object(
						'type', 'Feature',
						'geometry', ST_AsGeoJSON(s.geom)::JSON,
						'properties', json_object(
							'cusec', s.cusec,
							'municipality', s.municipality,
							'km2', round(s.km2, 3),
							'families', h.families,
							'value', %s)) ORDER BY s.cusec), [])
				)::VARCHAR
				FROM census_sections s LEFT JOIN h USING (cusec)
				WHERE s.cpro = ?""".formatted(metric.sql), rs -> rs.getString(1), typology, year, province, province)
			.getFirst();
		return ResponseEntity.ok().contentType(GEO_JSON).body(geoJson);
	}

}
