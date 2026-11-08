package com.example.tasks.retirement;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class RetirementControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private Warehouse warehouse;

	@Test
	void parsesUploadedVidaLaboralPdf() throws Exception {
		var res = mvc.post()
			.uri("/api/retirement/work-history")
			.multipart()
			.file(new MockMultipartFile("file", "vida-laboral.pdf", "application/pdf",
					pdf(VidaLaboralParserTests.REPORT)))
			.exchange();

		assertThat(res).hasStatusOk();
		assertThat(res).bodyJson().extractingPath("$.totalDays").isEqualTo(6578);
		assertThat(res).bodyJson().extractingPath("$.periods.length()").isEqualTo(6);
	}

	@Test
	void rejectsFilesThatAreNotPdf() {
		assertThat(mvc.post()
			.uri("/api/retirement/work-history")
			.multipart()
			.file(new MockMultipartFile("file", "x.pdf", "application/pdf", "hola".getBytes())))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void plansWithWarehouseAssumptions() {
		warehouse.replacePoints("ecb.yc.sr_10y", List.of(new Point(LocalDate.of(2026, 10, 9), 3.51)));
		warehouse.replacePoints("ecb.yc.sr_30y", List.of(new Point(LocalDate.of(2026, 10, 9), 3.78)));
		warehouse.executeAll("CREATE OR REPLACE TABLE life_expectancy (geo VARCHAR, sex VARCHAR, age INTEGER, year INTEGER, value DOUBLE)",
				"INSERT INTO life_expectancy SELECT 'ES', 'F', a, 2024, 88 - a FROM range(40, 85) t(a)");

		var res = mvc.post().uri("/api/retirement/plan").contentType(MediaType.APPLICATION_JSON).content("""
				{"birthDate":"1980-05-01","sex":"FEMALE","employment":"EMPLOYEE","grossAnnualSalary":70000,
				 "contributionYears":20,"currentSavings":5000,"riskProfile":"BALANCED","dependents":1}""").exchange();

		assertThat(res).hasStatusOk();
		assertThat(res).bodyJson().extractingPath("$.rates.lifeTableYear").isEqualTo(2024);
		assertThat(res).bodyJson().extractingPath("$.monthlySaving").asNumber().satisfies(
				n -> assertThat(n.doubleValue()).isPositive());

		assertThat(mvc.post().uri("/api/retirement/plan").contentType(MediaType.APPLICATION_JSON).content("""
				{"birthDate":"1980-05-01","sex":"MALE","employment":"EMPLOYEE","grossAnnualSalary":70000,
				 "contributionYears":20,"riskProfile":"BALANCED"}""")).hasStatus(HttpStatus.CONFLICT);
	}

	/** Writes each line of {@code text} into a one-page PDF, the way the real report lays it out. */
	private static byte[] pdf(String text) throws Exception {
		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			doc.addPage(page);
			try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
				cs.beginText();
				cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7);
				cs.setLeading(9);
				cs.newLineAtOffset(20, 760);
				for (String line : text.split("\n")) {
					cs.showText(line);
					cs.newLine();
				}
				cs.endText();
			}
			doc.save(out);
			return out.toByteArray();
		}
	}

}
