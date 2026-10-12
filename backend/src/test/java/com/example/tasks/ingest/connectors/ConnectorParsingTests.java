package com.example.tasks.ingest.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tasks.warehouse.Point;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ConnectorParsingTests {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void parsesEcbCsvByMaturity() {
		String csv = """
				KEY,FREQ,REF_AREA,CURRENCY,PROVIDER_FM,INSTRUMENT_FM,PROVIDER_FM_ID,DATA_TYPE_FM,TIME_PERIOD,OBS_VALUE
				YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_10Y,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_10Y,2026-10-08,3.51918971
				YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_1Y,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_1Y,2026-10-08,2.8797845255
				""";
		Map<String, List<Point>> parsed = EcbYieldCurveConnector.parse(csv);
		assertThat(parsed).containsOnlyKeys("SR_10Y", "SR_1Y");
		assertThat(parsed.get("SR_10Y")).containsExactly(new Point(LocalDate.of(2026, 10, 8), 3.51918971));
	}

	@Test
	void parsesEurostatJsonStat() {
		String json = """
				{"value":{"0":83.1,"2":84.0},
				 "dimension":{"time":{"category":{"index":{"2022":0,"2023":1,"2024":2}}}}}""";
		assertThat(EurostatLifeExpectancyConnector.parse(mapper.readTree(json))).containsExactly(
				new Point(LocalDate.of(2022, 1, 1), 83.1), new Point(LocalDate.of(2024, 1, 1), 84.0));
	}

	@Test
	void parsesOpenMeteoDailySkippingNulls() {
		String json = """
				{"daily":{"time":["2026-10-11","2026-10-12"],"temperature_2m_max":[24.6,null]}}""";
		assertThat(OpenMeteoHeatConnector.parseDaily(mapper.readTree(json)))
			.containsExactly(new Point(LocalDate.of(2026, 10, 11), 24.6));
	}

}
