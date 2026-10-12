package com.example.tasks.ingest.connectors;

import com.example.tasks.ingest.DataConnector;
import com.example.tasks.ingest.PublicHttp;
import com.example.tasks.ingest.SeriesInfo;
import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ECB euro area AAA government yield curve (spot rates). Drives the discount rate used
 * for life insurance technical provisions: falling rates increase reserves.
 */
@Component
@Order(3)
public class EcbYieldCurveConnector implements DataConnector {

	static final List<String> MATURITIES = List.of("SR_1Y", "SR_10Y", "SR_30Y");

	private static final String URL = "https://data-api.ecb.europa.eu/service/data/YC/B.U2.EUR.4F.G_N_A.SV_C_YM."
			+ String.join("+", MATURITIES) + "?format=csvdata&detail=dataonly&startPeriod=2018-01-01";

	private final PublicHttp http;

	private final Warehouse warehouse;

	public EcbYieldCurveConnector(PublicHttp http, Warehouse warehouse) {
		this.http = http;
		this.warehouse = warehouse;
	}

	@Override
	public String id() {
		return "ecb";
	}

	@Override
	public String name() {
		return "BCE · Curva de tipos";
	}

	@Override
	public String description() {
		return "Tipos al contado de la deuda AAA de la zona euro (1, 10 y 30 años). Referencia para el tipo técnico de descuento de las provisiones de vida.";
	}

	@Override
	public String url() {
		return "https://data.ecb.europa.eu/data/datasets/YC";
	}

	@Override
	public List<SeriesInfo> ingest() throws Exception {
		Map<String, List<Point>> byMaturity = parse(http.getString(URL));
		List<SeriesInfo> infos = new ArrayList<>();
		byMaturity.forEach((maturity, points) -> {
			String code = "ecb.yc." + maturity.toLowerCase();
			warehouse.replacePoints(code, points);
			String years = maturity.substring(3, maturity.length() - 1);
			String term = years.equals("1") ? "1 año" : years + " años";
			infos.add(new SeriesInfo(code, "Tipo al contado " + term + " (AAA zona euro)", "%", "diaria",
					"Curva Svensson del BCE, bonos soberanos AAA."));
		});
		return infos;
	}

	static Map<String, List<Point>> parse(String csv) {
		String[] lines = csv.split("\\R");
		List<String> header = Arrays.asList(lines[0].split(","));
		int maturityCol = header.indexOf("DATA_TYPE_FM");
		int dateCol = header.indexOf("TIME_PERIOD");
		int valueCol = header.indexOf("OBS_VALUE");
		Map<String, List<Point>> result = new LinkedHashMap<>();
		for (int i = 1; i < lines.length; i++) {
			String[] f = lines[i].split(",", -1);
			if (f.length <= valueCol || f[valueCol].isBlank()) {
				continue;
			}
			result.computeIfAbsent(f[maturityCol], k -> new ArrayList<>())
				.add(new Point(LocalDate.parse(f[dateCol]), Double.parseDouble(f[valueCol])));
		}
		return result;
	}

}
