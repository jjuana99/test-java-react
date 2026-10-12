package com.example.tasks.warehouse;

import java.sql.Array;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ad-hoc queries over the raw datasets landed in the warehouse. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

	public record TableStats(String name, long rows, long columns) {
	}

	public record AgeGroupExcess(String ageGroup, String label, double observed, double expected,
			double excessPct) {
	}

	public record ProvinceMarket(int code, String name, long families, String topTypology, String topTypologyName,
			double topShare) {
	}

	/** A Habits household typology with its official name, group and the income bands of its subtypes. */
	public record Typology(String code, String name, String group, List<String> incomeBands, long families,
			double share) {
	}

	private final Warehouse warehouse;

	public AnalyticsController(Warehouse warehouse) {
		this.warehouse = warehouse;
	}

	@GetMapping("/tables")
	public List<TableStats> tables() {
		return warehouse.query("""
				SELECT table_name, estimated_size, column_count FROM duckdb_tables()
				ORDER BY estimated_size DESC""",
				rs -> new TableStats(rs.getString(1), rs.getLong(2), rs.getLong(3)));
	}

	/** National excess mortality by age group over the last {@code days} days with complete data. */
	@GetMapping("/momo/age-groups")
	public List<AgeGroupExcess> momoAgeGroups(@RequestParam(defaultValue = "28") int days,
			@RequestParam(defaultValue = "7") int reportingLagDays) {
		if (!warehouse.tableExists("momo_raw")) {
			return List.of();
		}
		return warehouse.query("""
				WITH bounds AS (SELECT max(fecha_defuncion) - ?::INTEGER AS hi FROM momo_raw)
				SELECT cod_gedad, any_value(nombre_gedad), sum(defunciones_observadas), sum(defunciones_estimadas_base),
				       100 * (sum(defunciones_observadas) / sum(defunciones_estimadas_base) - 1)
				FROM momo_raw, bounds
				WHERE ambito = 'nacional' AND cod_sexo = 'all' AND cod_gedad <> 'all'
				  AND fecha_defuncion > hi - ?::INTEGER AND fecha_defuncion <= hi
				GROUP BY cod_gedad
				ORDER BY CASE cod_gedad WHEN '0-14' THEN 1 WHEN '15-44' THEN 2 WHEN '45-64' THEN 3
				         WHEN '65-74' THEN 4 WHEN '75-84' THEN 5 WHEN '+65' THEN 6 ELSE 7 END""",
				rs -> new AgeGroupExcess(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4),
						rs.getDouble(5)),
				reportingLagDays, days);
	}

	@GetMapping("/momo/period")
	public List<LocalDate> momoPeriod() {
		if (!warehouse.tableExists("momo_raw")) {
			return List.of();
		}
		return warehouse.query("SELECT min(fecha_defuncion), max(fecha_defuncion) FROM momo_raw",
				rs -> List.of(rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate()))
			.getFirst();
	}

	/** Families per province for a Habits year, with the dominant household typology. */
	@GetMapping("/habits/provinces")
	public List<ProvinceMarket> habitsProvinces(@RequestParam(defaultValue = "2011") int year) {
		if (!warehouse.tableExists("habits_raw")) {
			return List.of();
		}
		return warehouse.query("""
				WITH t AS (
					SELECT CO_PROV AS p, CO_TIPOL AS tipol, sum(NU_FAMILIAS) AS fam
					FROM habits_raw WHERE NU_ANO = ? GROUP BY ALL
				), totals AS (SELECT p, fam AS total FROM t WHERE tipol = 'Z'),
				top AS (SELECT p, arg_max(tipol, fam) AS tipol, max(fam) AS fam FROM t WHERE tipol <> 'Z' GROUP BY p)
				SELECT totals.p, coalesce(pr.name, 'Provincia ' || totals.p), totals.total, top.tipol, %s,
				       100 * top.fam / totals.total
				FROM totals JOIN top USING (p) LEFT JOIN provinces pr ON pr.co_prov = totals.p %s
				ORDER BY totals.total DESC""".formatted(hasCatalog() ? "ty.name" : "NULL",
				hasCatalog() ? "LEFT JOIN habits_typologies ty ON ty.code = top.tipol" : ""),
				rs -> new ProvinceMarket(rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getString(4),
						rs.getString(5), rs.getDouble(6)),
				year);
	}

	/** Household typologies with their national weight in {@code year}. */
	@GetMapping("/habits/typologies")
	public List<Typology> habitsTypologies(@RequestParam(defaultValue = "2011") int year) {
		if (!warehouse.tableExists("habits_raw")) {
			return List.of();
		}
		boolean catalog = hasCatalog();
		return warehouse.query("""
				WITH fam AS (
					SELECT CO_TIPOL AS code, sum(NU_FAMILIAS) AS f
					FROM habits_raw WHERE NU_ANO = ? AND CO_TIPOL <> 'Z' GROUP BY 1
				)%s
				SELECT fam.code, %s, fam.f, 100 * fam.f / sum(fam.f) OVER ()
				FROM fam %s
				ORDER BY fam.code""".formatted(
				catalog ? ", bands AS (SELECT typology AS code, list(band ORDER BY num) AS bands FROM habits_subtypologies GROUP BY 1)" : "",
				catalog ? "t.name, t.group_name, b.bands" : "NULL, NULL, NULL",
				catalog ? "LEFT JOIN habits_typologies t USING (code) LEFT JOIN bands b USING (code)" : ""),
				rs -> {
					Array bands = rs.getArray(4);
					List<String> distinct = bands == null ? List.of()
							: Arrays.stream((Object[]) bands.getArray()).map(String::valueOf).distinct().toList();
					return new Typology(rs.getString(1), rs.getString(2), rs.getString(3), distinct, rs.getLong(5),
							rs.getDouble(6));
				}, year);
	}

	private boolean hasCatalog() {
		return warehouse.tableExists("habits_typologies") && warehouse.tableExists("habits_subtypologies");
	}

}
