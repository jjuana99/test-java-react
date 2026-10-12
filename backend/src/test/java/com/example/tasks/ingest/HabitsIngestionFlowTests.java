package com.example.tasks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tasks.ingest.connectors.HabitsConnector;
import com.example.tasks.series.SeriesRepository;
import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class HabitsIngestionFlowTests {

	private static final String HEADER = "\"NU_ANO\",\"CO_CCAA\",\"CO_PROV\",\"CO_MUNI\",\"CO_DISTMUNI\",\"CO_SECCION\",\"CO_TIPOL\",\"CO_HASECCION\",\"NU_FAMILIAS\",\"LI_USER\",\"FH_CARGA\",\"FH_ULTMOD\",\"NU_CARGA\"\n";

	@Autowired
	private Warehouse warehouse;

	@Autowired
	private IngestionService ingestion;

	@Autowired
	private SeriesRepository series;

	@Autowired
	private MockMvcTester mvc;

	@TempDir
	Path tmp;

	@Test
	void loadsRawCsvDerivesSeriesAndRaisesAlerts() throws Exception {
		Path csv = tmp.resolve("habits.csv");
		Files.writeString(csv, HEADER + row(2010, 28, "A", 60) + row(2010, 28, "B", 40) + row(2010, 28, "Z", 100)
				+ row(2011, 28, "A", 80) + row(2011, 28, "B", 40) + row(2011, 28, "Z", 120));
		Path catalog = Files.createDirectory(tmp.resolve("catalog"));
		writeLatin1(catalog.resolve("SIEL_T_A_HCTI_CATTIPOL_DATA_TABLE.dsv"), """
				"CO_TIPOL";"NU_TIPOL";"LI_TIPOL";"FH_ULTMOD"
				"A";1;"Con adolescentes alta";22/10/12
				"B";2;"Con jóvenes alta";22/10/12
				""");
		writeLatin1(catalog.resolve("SIEL_T_A_HCST_CATSUBTIPOL_DATA_TABLE.dsv"), """
				"CO_SUBTIPOL";"NU_SUBTIPOL";"LI_SUBTIPOL";"FH_ULTMOD"
				"A1";1001;"Con adolescentes alta más de 40.000 Euros";22/10/12
				"A2";1002;"Con adolescentes alta entre 20.000 y 30.000 Euros";22/10/12
				"B1";2001;"Con jóvenes alta más de 40.000 Euros";22/10/12
				""");
		writeLatin1(catalog.resolve("SIEL_T_M_HCAT_AGGTIPOL_DATA_TABLE.dsv"), """
				"CO_TIPOL";"CO_AGGTIPOL";"LI_USER";"FH_CARGA";"FH_ULTMOD";"NU_CARGA"
				"A";3;"TIPOLOGIA";18/05/10;18/05/10;0
				""");
		writeLatin1(catalog.resolve("SIEL_T_M_HCAG_MAEAGGTIPOL_DATA_TABLE.dsv"), """
				"CO_AGGTIPOL";"LI_AGGTIPOL";"LI_USER";"FH_CARGA";"FH_ULTMOD";"NU_CARGA"
				3;"Hogares con Adolescentes";"TIPOLOGIA";14/07/10;14/07/10;0
				""");
		HabitsConnector connector = new HabitsConnector(warehouse, csv.toString(), catalog.toString());

		ingestion.execute(connector, ingestion.begin("habits").getId());

		assertThat(series.findByCode("habits.es.cuota.B")).get()
			.satisfies(s -> assertThat(s.getName()).isEqualTo("Cuota Con jóvenes alta (B) · España"));
		var typologies = mvc.get().uri("/api/analytics/habits/typologies?year=2011").exchange();
		assertThat(typologies).bodyJson().extractingPath("$[0].name").isEqualTo("Con adolescentes alta");
		assertThat(typologies).bodyJson().extractingPath("$[0].group").isEqualTo("Hogares con Adolescentes");
		assertThat(typologies).bodyJson()
			.extractingPath("$[0].incomeBands")
			.asArray()
			.containsExactly("más de 40.000 Euros", "entre 20.000 y 30.000 Euros");
		assertThat(typologies).bodyJson().extractingPath("$[1].group").isNull();
		assertThat(mvc.get().uri("/api/analytics/habits/provinces?year=2011")).bodyJson()
			.extractingPath("$[0].topTypologyName")
			.isEqualTo("Con adolescentes alta");

		assertThat(warehouse.points("habits.prov.28.total", LocalDate.of(1900, 1, 1))).containsExactly(
				new Point(LocalDate.of(2010, 1, 1), 100), new Point(LocalDate.of(2011, 1, 1), 120));
		assertThat(warehouse.points("habits.es.cuota.A", LocalDate.of(1900, 1, 1)))
			.extracting(Point::value)
			.containsExactly(60.0, 200.0 / 3);
		assertThat(series.findByCode("habits.prov.28.total")).get()
			.satisfies(s -> assertThat(s.getName()).isEqualTo("Familias totales · Madrid"));

		// the seeded "Mercado Madrid" rule (> 2 % change) fires on the +20 % jump
		assertThat(mvc.get().uri("/api/alerts")).bodyJson()
			.extractingPath("$[?(@.seriesCode == 'habits.prov.28.total')].value")
			.asArray()
			.containsExactly(120.0);

		var rule = mvc.post().uri("/api/alert-rules")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"Cuota A > 50 %","seriesCode":"habits.es.cuota.A","condition":"ABOVE",
					 "threshold":50,"severity":"WARNING","lookback":5}""")
			.exchange();
		assertThat(rule).hasStatus(HttpStatus.CREATED);
		assertThat(mvc.get().uri("/api/alerts")).bodyJson()
			.extractingPath("$[?(@.seriesCode == 'habits.es.cuota.A')]")
			.asArray()
			.hasSize(2);

		assertThat(mvc.post().uri("/api/alert-rules")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"x","seriesCode":"no.existe","condition":"ABOVE","threshold":1,"severity":"INFO"}"""))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	/** The Oracle exports are ISO-8859-1. */
	private static void writeLatin1(Path file, String content) throws Exception {
		Files.writeString(file, content, StandardCharsets.ISO_8859_1);
	}

	private static String row(int year, int prov, String tipol, int families) {
		return "%d,1,%d,79,1,\"003\",\"%s\",\"0307901003\",%d,\"TIPOLOGIA\",16/06/10,16/06/10,2\n".formatted(year,
				prov, tipol, families);
	}

}
