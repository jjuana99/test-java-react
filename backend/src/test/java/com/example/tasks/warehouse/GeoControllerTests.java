package com.example.tasks.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class GeoControllerTests {

	@Autowired
	private Warehouse warehouse;

	@Autowired
	private MockMvcTester mvc;

	@Test
	void joinsSectionsWithHabitsAsGeoJson() {
		warehouse.loadSpatial();
		warehouse.executeAll("""
				CREATE OR REPLACE TABLE census_sections AS
				SELECT * FROM (VALUES
					('2807901001', 28, 'Madrid', 0.5, ST_GeomFromText('POLYGON((-3.70 40.41, -3.69 40.41, -3.69 40.42, -3.70 40.41))')),
					('2807901002', 28, 'Madrid', 2.0, ST_GeomFromText('POLYGON((-3.69 40.41, -3.68 40.41, -3.68 40.42, -3.69 40.41))')),
					('0800101001', 8, 'Abrera', 1.0, ST_GeomFromText('POLYGON((2.0 41.5, 2.1 41.5, 2.1 41.6, 2.0 41.5))'))
				) t(cusec, cpro, municipality, km2, geom)""", """
				CREATE OR REPLACE TABLE habits_raw AS
				SELECT * FROM (VALUES
					(2011, 28, '2807901001', 'Z', 400), (2011, 28, '2807901001', 'A', 100),
					(2010, 28, '2807901001', 'Z', 999)
				) t(NU_ANO, CO_PROV, CO_HASECCION, CO_TIPOL, NU_FAMILIAS)""");

		var res = mvc.get().uri("/api/geo/sections?province=28&year=2011&metric=SHARE&typology=A").exchange();

		assertThat(res).hasStatusOk();
		assertThat(res).bodyJson().extractingPath("$.features.length()").isEqualTo(2);
		assertThat(res).bodyJson().extractingPath("$.features[0].geometry.type").isEqualTo("Polygon");
		assertThat(res).bodyJson().extractingPath("$.features[0].properties.families").isEqualTo(400);
		assertThat(res).bodyJson().extractingPath("$.features[0].properties.value").isEqualTo(25.0);
		// section without Habits data is still drawn, with null values
		assertThat(res).bodyJson().extractingPath("$.features[1].properties.value").isNull();

		assertThat(mvc.get().uri("/api/geo/sections?province=28&metric=DENSITY")).bodyJson()
			.extractingPath("$.features[0].properties.value")
			.isEqualTo(800.0);
		assertThat(mvc.get().uri("/api/geo/sections?province=28&typology=A';--"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

}
