package com.example.tasks.ingest.connectors;

import com.example.tasks.ingest.DataConnector;
import com.example.tasks.ingest.PublicHttp;
import com.example.tasks.ingest.SeriesInfo;
import com.example.tasks.warehouse.Warehouse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * MoMo daily mortality monitoring (Instituto de Salud Carlos III). The full CSV (~700 MB,
 * every region x sex x age group since 2015) is landed raw in the warehouse as
 * {@code momo_raw}; alertable series are derived from it with SQL.
 */
@Component
@Order(1)
public class MomoMortalityConnector implements DataConnector {

	private static final String URL = "https://momo.isciii.es/public/momo/data";

	private final PublicHttp http;

	private final Warehouse warehouse;

	public MomoMortalityConnector(PublicHttp http, Warehouse warehouse) {
		this.http = http;
		this.warehouse = warehouse;
	}

	@Override
	public String id() {
		return "momo";
	}

	@Override
	public String name() {
		return "ISCIII · MoMo mortalidad diaria";
	}

	@Override
	public String description() {
		return "Defunciones observadas y esperadas por CCAA, sexo y edad desde 2015. Base para detectar exceso de mortalidad (siniestralidad de vida riesgo).";
	}

	@Override
	public String url() {
		return "https://momo.isciii.es/panel_momo/";
	}

	@Override
	public List<SeriesInfo> ingest() throws Exception {
		Path file = http.download(URL, ".csv");
		try {
			load(file);
		}
		finally {
			Files.deleteIfExists(file);
		}
		return seriesInfo();
	}

	void load(Path csv) {
		String path = csv.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
		warehouse.executeAll("""
				CREATE OR REPLACE TABLE momo_raw AS
				SELECT * FROM read_csv('%s', header = true, nullstr = ['NA', ''],
					types = {'cod_ambito': 'VARCHAR', 'cod_ine_ambito': 'VARCHAR',
					         'cod_sexo': 'VARCHAR', 'cod_gedad': 'VARCHAR', 'fecha_defuncion': 'DATE',
					         'defunciones_observadas': 'DOUBLE', 'defunciones_estimadas_base': 'DOUBLE',
					         'defunciones_estimadas_base_q01': 'DOUBLE', 'defunciones_estimadas_base_q99': 'DOUBLE',
					         'defunciones_atrib_exc_temp': 'DOUBLE', 'defunciones_atrib_def_temp': 'DOUBLE'})"""
			.formatted(path),
				"DELETE FROM points WHERE series_code LIKE 'momo.%'",
				"""
				INSERT INTO points
				WITH base AS (
					SELECT CASE WHEN ambito = 'nacional' THEN 'es' ELSE lower(cod_ambito) END AS amb,
					       CASE WHEN cod_gedad = 'all' THEN 'total' ELSE '85' END AS edad,
					       fecha_defuncion AS d,
					       defunciones_observadas AS obs,
					       defunciones_estimadas_base AS esp,
					       defunciones_atrib_exc_temp AS calor
					FROM momo_raw
					WHERE cod_sexo = 'all' AND cod_gedad IN ('all', '+85') AND ambito IN ('nacional', 'ccaa')
				), w AS (
					SELECT *, 100 * (sum(obs) OVER win / nullif(sum(esp) OVER win, 0) - 1) AS exc7
					FROM base
					WINDOW win AS (PARTITION BY amb, edad ORDER BY d ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)
				)
				SELECT 'momo.' || amb || '.exceso7d.' || edad, d, exc7 FROM w WHERE exc7 IS NOT NULL
				UNION ALL
				SELECT 'momo.' || amb || '.defunciones', d, obs FROM w WHERE edad = 'total' AND obs IS NOT NULL
				UNION ALL
				SELECT 'momo.es.esperadas', d, esp FROM w WHERE amb = 'es' AND edad = 'total' AND esp IS NOT NULL
				UNION ALL
				SELECT 'momo.es.calor', d, calor FROM w WHERE amb = 'es' AND edad = 'total' AND calor IS NOT NULL
				""");
	}

	List<SeriesInfo> seriesInfo() {
		List<String[]> regions = new ArrayList<>();
		regions.add(new String[] { "es", "España" });
		regions.addAll(warehouse.query("""
				SELECT DISTINCT lower(cod_ambito), nombre_ambito FROM momo_raw
				WHERE ambito = 'ccaa' ORDER BY 2""", rs -> new String[] { rs.getString(1), rs.getString(2) }));
		String excessNote = "Defunciones observadas frente a esperadas, media móvil de 7 días. Los últimos días están infranotificados.";
		List<SeriesInfo> infos = new ArrayList<>();
		for (String[] r : regions) {
			infos.add(new SeriesInfo("momo." + r[0] + ".exceso7d.total", "Exceso de mortalidad 7d · " + r[1], "%",
					"diaria", excessNote));
			infos.add(new SeriesInfo("momo." + r[0] + ".exceso7d.85", "Exceso de mortalidad 7d (85+) · " + r[1], "%",
					"diaria", excessNote));
			infos.add(new SeriesInfo("momo." + r[0] + ".defunciones", "Defunciones observadas · " + r[1],
					"defunciones", "diaria", "Todas las edades y sexos."));
		}
		infos.add(new SeriesInfo("momo.es.esperadas", "Defunciones esperadas · España", "defunciones", "diaria",
				"Línea base del modelo MoMo."));
		infos.add(new SeriesInfo("momo.es.calor", "Defunciones atribuibles al exceso de temperatura · España",
				"defunciones", "diaria", "Estimación MoMo de muertes atribuibles al calor."));
		return infos;
	}

}
