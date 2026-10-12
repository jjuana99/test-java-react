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
		return infos;
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
