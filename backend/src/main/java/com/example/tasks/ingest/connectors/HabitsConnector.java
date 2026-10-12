package com.example.tasks.ingest.connectors;

import com.example.tasks.ingest.DataConnector;
import com.example.tasks.ingest.SeriesInfo;
import com.example.tasks.warehouse.Warehouse;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Habits household segmentation 2007-2011: number of families per census section and
 * typology (A-R, with Z holding the section total). Loaded from a local CSV into
 * {@code habits_raw}; it sizes the addressable market per segment and province.
 */
@Component
@Order(5)
public class HabitsConnector implements DataConnector {

	public static final Map<Integer, String> PROVINCES = new TreeMap<>(Map.ofEntries(Map.entry(1, "Araba/Álava"),
			Map.entry(2, "Albacete"), Map.entry(3, "Alicante"), Map.entry(4, "Almería"), Map.entry(5, "Ávila"),
			Map.entry(6, "Badajoz"), Map.entry(7, "Illes Balears"), Map.entry(8, "Barcelona"), Map.entry(9, "Burgos"),
			Map.entry(10, "Cáceres"), Map.entry(11, "Cádiz"), Map.entry(12, "Castellón"), Map.entry(13, "Ciudad Real"),
			Map.entry(14, "Córdoba"), Map.entry(15, "A Coruña"), Map.entry(16, "Cuenca"), Map.entry(17, "Girona"),
			Map.entry(18, "Granada"), Map.entry(19, "Guadalajara"), Map.entry(20, "Gipuzkoa"), Map.entry(21, "Huelva"),
			Map.entry(22, "Huesca"), Map.entry(23, "Jaén"), Map.entry(24, "León"), Map.entry(25, "Lleida"),
			Map.entry(26, "La Rioja"), Map.entry(27, "Lugo"), Map.entry(28, "Madrid"), Map.entry(29, "Málaga"),
			Map.entry(30, "Murcia"), Map.entry(31, "Navarra"), Map.entry(32, "Ourense"), Map.entry(33, "Asturias"),
			Map.entry(34, "Palencia"), Map.entry(35, "Las Palmas"), Map.entry(36, "Pontevedra"),
			Map.entry(37, "Salamanca"), Map.entry(38, "Santa Cruz de Tenerife"), Map.entry(39, "Cantabria"),
			Map.entry(40, "Segovia"), Map.entry(41, "Sevilla"), Map.entry(42, "Soria"), Map.entry(43, "Tarragona"),
			Map.entry(44, "Teruel"), Map.entry(45, "Toledo"), Map.entry(46, "Valencia"), Map.entry(47, "Valladolid"),
			Map.entry(48, "Bizkaia"), Map.entry(49, "Zamora"), Map.entry(50, "Zaragoza"), Map.entry(51, "Ceuta"),
			Map.entry(52, "Melilla")));

	private final Warehouse warehouse;

	private final Path file;

	/** Folder with the Oracle catalog exports (*.dsv) that name the typologies. */
	private final Path catalogDir;

	public HabitsConnector(Warehouse warehouse, @Value("${app.habits.file:../Data/Habbits2007-2011.csv}") String file,
			@Value("${app.habits.catalog-dir:../Data/BDCOmpleta}") String catalogDir) {
		this.warehouse = warehouse;
		this.file = Path.of(file).toAbsolutePath().normalize();
		this.catalogDir = Path.of(catalogDir).toAbsolutePath().normalize();
	}

	@Override
	public String id() {
		return "habits";
	}

	@Override
	public String name() {
		return "Habits · Tipologías de hogar";
	}

	@Override
	public String description() {
		return "Familias por sección censal y tipología de hogar (2007-2011). Dimensiona el mercado potencial de vida por segmento y provincia.";
	}

	@Override
	public String url() {
		return file.toUri().toString();
	}

	@Override
	public boolean scheduled() {
		return false;
	}

	@Override
	public List<SeriesInfo> ingest() throws Exception {
		if (!Files.isRegularFile(file)) {
			throw new FileNotFoundException("No se encuentra el fichero Habits en " + file);
		}
		load(file);
		loadCatalog();
		return seriesInfo();
	}

	private static final String TYPOLOGIES = "SIEL_T_A_HCTI_CATTIPOL_DATA_TABLE.dsv";

	private static final String SUBTYPOLOGIES = "SIEL_T_A_HCST_CATSUBTIPOL_DATA_TABLE.dsv";

	private static final String GROUP_MAP = "SIEL_T_M_HCAT_AGGTIPOL_DATA_TABLE.dsv";

	private static final String GROUPS = "SIEL_T_M_HCAG_MAEAGGTIPOL_DATA_TABLE.dsv";

	/**
	 * Loads the typology catalog (names, group, income bands of the subtypologies) from the
	 * Oracle exports. Without them the tables are left empty and typologies show their code.
	 */
	void loadCatalog() {
		boolean present = List.of(TYPOLOGIES, SUBTYPOLOGIES, GROUP_MAP, GROUPS)
			.stream()
			.allMatch(f -> Files.isRegularFile(catalogDir.resolve(f)));
		if (!present) {
			warehouse.executeAll(
					"CREATE OR REPLACE TABLE habits_typologies (code VARCHAR, num INTEGER, name VARCHAR, group_name VARCHAR)",
					"CREATE OR REPLACE TABLE habits_subtypologies (typology VARCHAR, num INTEGER, name VARCHAR, band VARCHAR)");
			return;
		}
		warehouse.executeAll("""
				CREATE OR REPLACE TABLE habits_typologies AS
				SELECT t.CO_TIPOL AS code, t.NU_TIPOL AS num, t.LI_TIPOL AS name, g.LI_AGGTIPOL AS group_name
				FROM %s t
				LEFT JOIN %s m USING (CO_TIPOL)
				LEFT JOIN %s g USING (CO_AGGTIPOL)
				ORDER BY t.NU_TIPOL""".formatted(dsv(TYPOLOGIES), dsv(GROUP_MAP), dsv(GROUPS)), """
				CREATE OR REPLACE TABLE habits_subtypologies AS
				SELECT t.code AS typology, s.NU_SUBTIPOL AS num, s.LI_SUBTIPOL AS name,
				       trim(replace(s.LI_SUBTIPOL, t.name, '')) AS band
				FROM %s s JOIN habits_typologies t ON t.num = s.NU_SUBTIPOL // 1000
				ORDER BY s.NU_SUBTIPOL""".formatted(dsv(SUBTYPOLOGIES)));
	}

	private String dsv(String name) {
		String path = catalogDir.resolve(name).toString().replace('\\', '/').replace("'", "''");
		return "read_csv('%s', delim = ';', header = true, encoding = 'latin-1')".formatted(path);
	}

	/** Typology names by code; empty when the catalog is not available. */
	Map<String, String[]> typologyNames() {
		Map<String, String[]> names = new TreeMap<>();
		if (warehouse.tableExists("habits_typologies")) {
			warehouse.query("SELECT code, name, group_name FROM habits_typologies",
					rs -> names.put(rs.getString(1), new String[] { rs.getString(2), rs.getString(3) }));
		}
		return names;
	}

	void load(Path csv) {
		String path = csv.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
		String provinces = PROVINCES.entrySet()
			.stream()
			.map(e -> "(" + e.getKey() + ", '" + e.getValue() + "')")
			.collect(Collectors.joining(", "));
		warehouse.executeAll("""
				CREATE OR REPLACE TABLE habits_raw AS
				SELECT * FROM read_csv('%s', header = true,
					types = {'CO_SECCION': 'VARCHAR', 'CO_TIPOL': 'VARCHAR', 'CO_HASECCION': 'VARCHAR',
					         'FH_CARGA': 'VARCHAR', 'FH_ULTMOD': 'VARCHAR'})""".formatted(path),
				"CREATE OR REPLACE TABLE provinces AS SELECT * FROM (VALUES " + provinces + ") t(co_prov, name)",
				"DELETE FROM points WHERE series_code LIKE 'habits.%'",
				"""
				INSERT INTO points
				WITH by_tipol AS (
					SELECT NU_ANO AS y, CO_TIPOL AS t, sum(NU_FAMILIAS) AS fam
					FROM habits_raw GROUP BY ALL
				)
				SELECT 'habits.es.total', make_date(y, 1, 1), fam FROM by_tipol WHERE t = 'Z'
				UNION ALL
				SELECT 'habits.es.tipol.' || t, make_date(y, 1, 1), fam FROM by_tipol WHERE t <> 'Z'
				UNION ALL
				SELECT 'habits.es.cuota.' || t, make_date(y, 1, 1),
				       100 * fam / sum(fam) OVER (PARTITION BY y)
				FROM by_tipol WHERE t <> 'Z'
				UNION ALL
				SELECT 'habits.prov.' || lpad(CO_PROV::VARCHAR, 2, '0') || '.total', make_date(NU_ANO, 1, 1),
				       sum(NU_FAMILIAS)
				FROM habits_raw WHERE CO_TIPOL = 'Z' GROUP BY CO_PROV, NU_ANO
				""");
	}

	List<SeriesInfo> seriesInfo() {
		List<SeriesInfo> infos = new ArrayList<>();
		infos.add(new SeriesInfo("habits.es.total", "Familias totales · España", "familias", "anual",
				"Suma de la tipología Z (total de la sección censal)."));
		List<String> typologies = warehouse.query(
				"SELECT DISTINCT CO_TIPOL FROM habits_raw WHERE CO_TIPOL <> 'Z' ORDER BY 1", rs -> rs.getString(1));
		Map<String, String[]> names = typologyNames();
		for (String t : typologies) {
			String[] n = names.get(t);
			String label = n == null ? "tipología " + t : n[0] + " (" + t + ")";
			String about = n == null ? "Segmento de hogar " + t + "."
					: "Tipología " + t + ": " + n[0] + (n[1] != null ? ". Grupo: " + n[1] + "." : ".");
			infos.add(new SeriesInfo("habits.es.tipol." + t, "Familias " + label + " · España", "familias", "anual",
					about));
			infos.add(new SeriesInfo("habits.es.cuota." + t, "Cuota " + label + " · España", "%", "anual",
					about + " Peso sobre el total de familias clasificadas."));
		}
		List<Integer> provinces = warehouse.query("SELECT DISTINCT CO_PROV FROM habits_raw ORDER BY 1",
				rs -> rs.getInt(1));
		for (int p : provinces) {
			infos.add(new SeriesInfo("habits.prov.%02d.total".formatted(p),
					"Familias totales · " + PROVINCES.getOrDefault(p, "Provincia " + p), "familias", "anual",
					"Tipología Z agregada por provincia."));
		}
		return infos;
	}

}
