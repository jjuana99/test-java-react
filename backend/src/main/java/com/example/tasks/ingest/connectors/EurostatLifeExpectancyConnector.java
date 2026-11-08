package com.example.tasks.ingest.connectors;

import com.example.tasks.ingest.DataConnector;
import com.example.tasks.ingest.PublicHttp;
import com.example.tasks.ingest.SeriesInfo;
import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Eurostat life expectancy (demo_mlexpec): the core longevity-risk indicator for annuities. */
@Component
@Order(4)
public class EurostatLifeExpectancyConnector implements DataConnector {

	private static final String BASE = "https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/demo_mlexpec?sex=T&sinceTimePeriod=2000&lang=EN";

	private record Query(String code, String geo, String age, String name) {
	}

	private static final List<Query> QUERIES = List.of(
			new Query("eurostat.lifeexp.es.0", "ES", "Y_LT1", "Esperanza de vida al nacer · España"),
			new Query("eurostat.lifeexp.es.65", "ES", "Y65", "Esperanza de vida a los 65 · España"),
			new Query("eurostat.lifeexp.eu.0", "EU27_2020", "Y_LT1", "Esperanza de vida al nacer · UE-27"),
			new Query("eurostat.lifeexp.eu.65", "EU27_2020", "Y65", "Esperanza de vida a los 65 · UE-27"));

	private final PublicHttp http;

	private final Warehouse warehouse;

	private final ObjectMapper mapper;

	public EurostatLifeExpectancyConnector(PublicHttp http, Warehouse warehouse, ObjectMapper mapper) {
		this.http = http;
		this.warehouse = warehouse;
		this.mapper = mapper;
	}

	@Override
	public String id() {
		return "eurostat";
	}

	@Override
	public String name() {
		return "Eurostat · Esperanza de vida";
	}

	@Override
	public String description() {
		return "Esperanza de vida al nacer y a los 65 años (España y UE-27). Mide el riesgo de longevidad de rentas vitalicias.";
	}

	@Override
	public String url() {
		return "https://ec.europa.eu/eurostat/databrowser/view/demo_mlexpec/default/table";
	}

	@Override
	public List<SeriesInfo> ingest() throws Exception {
		List<SeriesInfo> infos = new ArrayList<>();
		for (Query q : QUERIES) {
			String json = http.getString(BASE + "&geo=" + q.geo() + "&age=" + q.age());
			warehouse.replacePoints(q.code(), parse(mapper.readTree(json)));
			infos.add(new SeriesInfo(q.code(), q.name(), "años", "anual", "Eurostat demo_mlexpec, ambos sexos."));
		}
		loadLifeTable();
		return infos;
	}

	static final int MIN_AGE = 40;

	static final int MAX_AGE = 85;

	/** One cell of the life-expectancy table: remaining years at {@code age}. */
	record LifeExpectancy(String sex, int age, int year, double value) {
	}

	/**
	 * Lands the Spanish life-expectancy table by sex and single age (40-85) for recent years
	 * as {@code life_expectancy}, used by the retirement planner.
	 */
	private void loadLifeTable() throws Exception {
		StringBuilder ages = new StringBuilder();
		for (int a = MIN_AGE; a < MAX_AGE; a++) {
			ages.append("&age=Y").append(a);
		}
		ages.append("&age=Y_GE85");
		List<LifeExpectancy> rows = new ArrayList<>();
		for (String sex : List.of("F", "M", "T")) {
			String url = "https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/demo_mlexpec?geo=ES"
					+ "&sinceTimePeriod=2015&lang=EN&sex=" + sex + ages;
			rows.addAll(parseByAge(sex, mapper.readTree(http.getString(url))));
		}
		warehouse.inTransaction(c -> {
			try (var st = c.createStatement()) {
				st.execute("CREATE OR REPLACE TABLE life_expectancy "
						+ "(geo VARCHAR, sex VARCHAR, age INTEGER, year INTEGER, value DOUBLE)");
			}
			try (var ps = c.prepareStatement("INSERT INTO life_expectancy VALUES ('ES', ?, ?, ?, ?)")) {
				for (LifeExpectancy r : rows) {
					ps.setString(1, r.sex());
					ps.setInt(2, r.age());
					ps.setInt(3, r.year());
					ps.setDouble(4, r.value());
					ps.addBatch();
				}
				ps.executeBatch();
			}
		});
	}

	/** Parses a JSON-stat dataset whose only multi-valued dimensions are age and time (age before time). */
	static List<LifeExpectancy> parseByAge(String sex, JsonNode dataset) {
		JsonNode ageIndex = dataset.path("dimension").path("age").path("category").path("index");
		JsonNode timeIndex = dataset.path("dimension").path("time").path("category").path("index");
		JsonNode values = dataset.path("value");
		int timeSize = timeIndex.size();
		List<LifeExpectancy> rows = new ArrayList<>();
		for (Map.Entry<String, JsonNode> a : ageIndex.properties()) {
			int age = a.getKey().equals("Y_GE85") ? MAX_AGE : Integer.parseInt(a.getKey().substring(1));
			for (Map.Entry<String, JsonNode> t : timeIndex.properties()) {
				JsonNode v = values.path(String.valueOf(a.getValue().asInt() * timeSize + t.getValue().asInt()));
				if (v.isNumber()) {
					rows.add(new LifeExpectancy(sex, age, Integer.parseInt(t.getKey()), v.asDouble()));
				}
			}
		}
		return rows;
	}

	/** Parses a JSON-stat 2.0 dataset where only the time dimension has more than one category. */
	static List<Point> parse(JsonNode dataset) {
		JsonNode timeIndex = dataset.path("dimension").path("time").path("category").path("index");
		JsonNode values = dataset.path("value");
		List<Point> points = new ArrayList<>();
		for (Map.Entry<String, JsonNode> e : timeIndex.properties()) {
			JsonNode v = values.path(String.valueOf(e.getValue().asInt()));
			if (v.isNumber()) {
				points.add(new Point(LocalDate.of(Integer.parseInt(e.getKey()), 1, 1), v.asDouble()));
			}
		}
		return points;
	}

}
