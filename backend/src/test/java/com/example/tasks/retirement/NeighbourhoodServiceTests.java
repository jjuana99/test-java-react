package com.example.tasks.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.tasks.ingest.PublicHttp;
import com.example.tasks.warehouse.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class NeighbourhoodServiceTests {

	private final JsonMapper mapper = JsonMapper.builder().build();

	/** CartoCiudad answer for "calle alcala 100 madrid" (trimmed). */
	private static final String CARTOCIUDAD = """
			callback({"id":"13.PV.MUN_280790087391","province":"Madrid","provinceCode":"28","muni":"Madrid",\
			"muniCode":"28079","type":"portal","address":"ALCALA","postalCode":"28009","tip_via":"CALLE",\
			"lat":40.425067513547695,"lng":-3.6746686294123125,"portalNumber":100,"state":0})""";

	@Test
	void parsesCartoCiudadJsonp() {
		NeighbourhoodService.Location l = NeighbourhoodService.parse(mapper, CARTOCIUDAD);
		assertThat(l.label()).isEqualTo("CALLE ALCALA 100");
		assertThat(l.lat()).isCloseTo(40.425, within(0.001));
		assertThat(l.lng()).isCloseTo(-3.675, within(0.001));
		assertThat(l.postalCode()).isEqualTo("28009");
		assertThat(NeighbourhoodService.parse(mapper, "callback({\"lat\":null})")).isNull();
		assertThat(NeighbourhoodService.parse(mapper, "")).isNull();
	}

	/** Geocoder stub that always answers with the Alcalá 100 location. */
	private static PublicHttp geocoderReturning(String body) {
		return new PublicHttp() {
			@Override
			public String getString(String url) {
				return body;
			}
		};
	}

	@Test
	void findsSectionAndComparesWithProvinceAndSpain() throws Exception {
		Warehouse w = new Warehouse("jdbc:duckdb:");
		w.loadSpatial();
		w.executeAll("""
				CREATE TABLE census_sections AS SELECT * FROM (VALUES
					('2807901001', 28, 'Madrid', 0.5, ST_GeomFromText('POLYGON((-3.68 40.42, -3.67 40.42, -3.67 40.43, -3.68 40.43, -3.68 40.42))'))
				) t(cusec, cpro, municipality, km2, geom)""", """
				CREATE TABLE habits_raw AS SELECT * FROM (VALUES
					(2011, 28, '2807901001', 'A', 30), (2011, 28, '2807901001', 'R', 70), (2011, 28, '2807901001', 'Z', 100),
					(2011, 28, '2807901002', 'A', 50), (2011, 28, '2807901002', 'R', 50),
					(2011, 8, '0800101001', 'A', 300), (2011, 8, '0800101001', 'R', 100)
				) t(NU_ANO, CO_PROV, CO_HASECCION, CO_TIPOL, NU_FAMILIAS)""",
				"CREATE TABLE habits_typologies AS SELECT * FROM (VALUES ('A', 1, 'Con adolescentes alta', 'Hogares con Adolescentes'), ('R', 18, 'Inmigrantes', NULL)) t(code, num, name, group_name)");
		NeighbourhoodService service = new NeighbourhoodService(geocoderReturning(CARTOCIUDAD), w, mapper);

		Neighbourhood n = service.find("calle alcala 100 madrid");

		assertThat(n.cusec()).isEqualTo("2807901001");
		assertThat(n.families()).isEqualTo(100);
		var top = n.typologies().getFirst();
		assertThat(top.code()).isEqualTo("R");
		assertThat(top.name()).isEqualTo("Inmigrantes");
		assertThat(top.share()).isEqualTo(70.0);
		assertThat(top.provinceShare()).isEqualTo(60.0); // (70+50)/200
		assertThat(top.nationalShare()).isEqualTo(36.7); // 220/600
	}

	@Test
	void unknownAddressIsReportedAsNotFound() throws Exception {
		Warehouse w = new Warehouse("jdbc:duckdb:");
		w.executeAll("CREATE TABLE census_sections (cusec VARCHAR)", "CREATE TABLE habits_raw (x INT)");
		NeighbourhoodService service = new NeighbourhoodService(geocoderReturning("callback({})"), w, mapper);
		assertThatThrownBy(() -> service.find("calle que no existe")).isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("No encontramos esa dirección");
	}

}
