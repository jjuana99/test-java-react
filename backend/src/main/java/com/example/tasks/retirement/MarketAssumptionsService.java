package com.example.tasks.retirement;

import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MarketAssumptionsService {

	private final Warehouse warehouse;

	public MarketAssumptionsService(Warehouse warehouse) {
		this.warehouse = warehouse;
	}

	public MarketAssumptions load(PlanRequest.Sex sex) {
		List<Point> tenYear = warehouse.latestPoints("ecb.yc.sr_10y", 1);
		List<Point> thirtyYear = warehouse.latestPoints("ecb.yc.sr_30y", 1);
		if (tenYear.isEmpty() || thirtyYear.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Faltan los tipos del BCE: actualiza la fuente 'BCE · Curva de tipos' en el Resumen");
		}
		if (!warehouse.tableExists("life_expectancy")) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Falta la tabla de esperanza de vida: actualiza la fuente 'Eurostat' en el Resumen");
		}
		String code = switch (sex) {
			case FEMALE -> "F";
			case MALE -> "M";
			case UNSPECIFIED -> "T";
		};
		Map<Integer, Double> table = new TreeMap<>();
		int[] year = new int[1];
		warehouse.query("""
				SELECT age, value, year FROM life_expectancy
				WHERE geo = 'ES' AND sex = ? AND year = (SELECT max(year) FROM life_expectancy WHERE sex = ?)""",
				rs -> {
					table.put(rs.getInt(1), rs.getDouble(2));
					year[0] = rs.getInt(3);
					return null;
				}, code, code);
		if (table.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La tabla de esperanza de vida está vacía");
		}
		return new MarketAssumptions(tenYear.getFirst().value() / 100, thirtyYear.getFirst().value() / 100,
				tenYear.getFirst().date(), table, year[0]);
	}

}
