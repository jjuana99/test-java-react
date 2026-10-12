package com.example.tasks.ingest.connectors;

import com.example.tasks.ingest.DataConnector;
import com.example.tasks.ingest.PublicHttp;
import com.example.tasks.ingest.SeriesInfo;
import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Open-Meteo daily maximum temperature (last 7 days + 7-day forecast). Heat waves are a
 * well documented driver of excess mortality among the elderly.
 */
@Component
@Order(2)
public class OpenMeteoHeatConnector implements DataConnector {

	record City(String slug, String name, double lat, double lon) {
	}

	static final List<City> CITIES = List.of(new City("madrid", "Madrid", 40.4168, -3.7038),
			new City("sevilla", "Sevilla", 37.3891, -5.9845), new City("cordoba", "Córdoba", 37.8882, -4.7794),
			new City("barcelona", "Barcelona", 41.3874, 2.1686), new City("valencia", "Valencia", 39.4699, -0.3763),
			new City("zaragoza", "Zaragoza", 41.6488, -0.8891), new City("bilbao", "Bilbao", 43.2630, -2.9350));

	private final PublicHttp http;

	private final Warehouse warehouse;

	private final ObjectMapper mapper;

	public OpenMeteoHeatConnector(PublicHttp http, Warehouse warehouse, ObjectMapper mapper) {
		this.http = http;
		this.warehouse = warehouse;
		this.mapper = mapper;
	}

	@Override
	public String id() {
		return "meteo";
	}

	@Override
	public String name() {
		return "Open-Meteo · Temperaturas máximas";
	}

	@Override
	public String description() {
		return "Máxima diaria observada (7 días) y prevista (7 días) en grandes ciudades. Anticipa episodios de calor con sobremortalidad.";
	}

	@Override
	public String url() {
		return "https://open-meteo.com/";
	}

	@Override
	public List<SeriesInfo> ingest() throws Exception {
		String url = "https://api.open-meteo.com/v1/forecast?daily=temperature_2m_max&timezone=Europe%2FMadrid"
				+ "&past_days=7&forecast_days=7&latitude="
				+ CITIES.stream().map(c -> String.valueOf(c.lat())).collect(Collectors.joining(","))
				+ "&longitude=" + CITIES.stream().map(c -> String.valueOf(c.lon())).collect(Collectors.joining(","));
		JsonNode locations = mapper.readTree(http.getString(url));
		List<SeriesInfo> infos = new ArrayList<>();
		for (int i = 0; i < CITIES.size(); i++) {
			City city = CITIES.get(i);
			String code = "meteo.tmax." + city.slug();
			warehouse.replacePoints(code, parseDaily(locations.get(i)));
			infos.add(new SeriesInfo(code, "Temperatura máxima · " + city.name(), "°C", "diaria",
					"Incluye previsión a 7 días."));
		}
		return infos;
	}

	static List<Point> parseDaily(JsonNode location) {
		JsonNode times = location.path("daily").path("time");
		JsonNode temps = location.path("daily").path("temperature_2m_max");
		List<Point> points = new ArrayList<>();
		for (int i = 0; i < times.size(); i++) {
			if (temps.get(i).isNumber()) {
				points.add(new Point(LocalDate.parse(times.get(i).asString()), temps.get(i).asDouble()));
			}
		}
		return points;
	}

}
