package com.example.tasks.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class VidaLaboralParserTests {

	/** Text layout as PDFBox extracts a real report (identity and employers replaced by fictitious ones). */
	static final String REPORT = """
			INFORME DE VIDA LABORAL
			De los antecedentes obrantes en la Tesorería General de la Seguridad Social al día 21 de febrero de 2025 , resulta que D/Dª
			NOMBRE APELLIDO APELLIDO , nacido/a el , con
			Número de la Seguridad Social , D , domicilio en
			ha figurado en situación de alta en el Sistema de la Seguridad Social durante un total de
			18 Años
			6.578 días 0 meses
			4 días
			Presenta las situaciones que se relacionan en las sucesivas hojas del presente informe.
			INFORME DE VIDA LABORAL - SITUACIONES
			RÉGIMEN EMPRESA FE  DE TP
			GENERAL 09100000001 EMPRESA UNO, S.L. 03.09.2018 03.09.2018 --- 189 --- 09 2.364
			GENERAL 09100000001 EXCEDENCIA POR CUIDADO HIJOS. 30.06.2018 30.06.2018 02.09.2018 --- --- -- 65
			LABORALES
			GENERAL 09100000001 EMPRESA UNO, S.L. 01.01.2008 01.01.2008 29.06.2018 189 --- 09 3.574
			GENERAL ----------- PRESTACION DESEMPLEO. EXTINCION 04.09.2007 04.09.2007 04.11.2007 --- --- 10 62
			GENERAL 09100000002 EMPRESA DOS 03.03.2006 03.03.2006 02.06.2006 502 50,0 07 46
			GENERAL 09100000003 EMPRESA TRES TEMPORAL 09.07.2004 09.07.2004 12.07.2004 401 --- 10 4
			""";

	private final VidaLaboralParser parser = new VidaLaboralParser();

	@Test
	void readsSummaryAndSituations() {
		WorkHistory h = parser.parseText(REPORT);

		assertThat(h.reportDate()).isEqualTo(LocalDate.of(2025, 2, 21));
		assertThat(h.totalDays()).isEqualTo(6578);
		assertThat(h.years()).isEqualTo(18);
		assertThat(h.months()).isZero();
		assertThat(h.days()).isEqualTo(4);
		assertThat(h.contributionYears()).isEqualTo(18.02);
		assertThat(h.periods()).hasSize(6);
		assertThat(h.periods().getFirst()).satisfies(p -> {
			assertThat(p.employer()).isEqualTo("EMPRESA UNO, S.L.");
			assertThat(p.to()).isNull();
			assertThat(p.days()).isEqualTo(2364);
			assertThat(p.assimilated()).isFalse();
		});
		assertThat(h.periods().get(1).assimilated()).isTrue();
		assertThat(h.periods().get(3).assimilated()).isTrue();
		assertThat(h.firstRegistration()).isEqualTo(LocalDate.of(2004, 7, 9));
		assertThat(h.currentRegime()).isEqualTo("GENERAL");
		assertThat(h.currentlyRegistered()).isTrue();
	}

	@Test
	void readsHomeAddressWhenPresent() {
		String withAddress = REPORT.replace("Número de la Seguridad Social , D , domicilio en\n",
				"Número de la Seguridad Social 28/1234567/89, D.N.I. 12345678Z, domicilio en\nCALLE MAYOR 12 3º B\n28013 MADRID (MADRID)\n");

		assertThat(parser.parseText(withAddress).address()).isEqualTo("CALLE MAYOR 12 3º B 28013 MADRID (MADRID)");
		// the sample report has the address redacted
		assertThat(parser.parseText(REPORT).address()).isNull();
		assertThat(parser.parseText(withAddress).toString()).doesNotContain("12345678Z").doesNotContain("28/1234567/89");
	}

	@Test
	void neverExposesIdentityData() {
		assertThat(parser.parseText(REPORT).toString()).doesNotContain("NOMBRE APELLIDO");
	}

	@Test
	void rejectsOtherDocuments() {
		assertThatThrownBy(() -> parser.parseText("Factura de la luz")).isInstanceOf(IllegalArgumentException.class);
	}

}
