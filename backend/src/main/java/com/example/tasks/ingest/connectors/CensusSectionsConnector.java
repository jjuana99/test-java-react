package com.example.tasks.ingest.connectors;

import com.example.tasks.ingest.DataConnector;
import com.example.tasks.ingest.PublicHttp;
import com.example.tasks.ingest.SeriesInfo;
import com.example.tasks.warehouse.Warehouse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * INE census section boundaries (Censo 2011, the vintage that matches Habits 2011). The
 * shapefile is read with DuckDB's spatial extension, simplified to ~10 m, reprojected to
 * WGS84 and stored as {@code census_sections}. It produces no series: it is the geometry
 * the section map joins against.
 */
@Component
@Order(6)
public class CensusSectionsConnector implements DataConnector {

	private static final String URL = "https://www.ine.es/censos2011_datos/cartografia_censo2011_nacional.zip";

	private static final String SHAPEFILE = "SECC_CPV_E_20111101_01_R_INE";

	private final PublicHttp http;

	private final Warehouse warehouse;

	public CensusSectionsConnector(PublicHttp http, Warehouse warehouse) {
		this.http = http;
		this.warehouse = warehouse;
	}

	@Override
	public String id() {
		return "secciones";
	}

	@Override
	public String name() {
		return "INE · Secciones censales 2011";
	}

	@Override
	public String description() {
		return "Contornos de las ~36.000 secciones censales del Censo 2011. Permiten cartografiar Habits a nivel de sección.";
	}

	@Override
	public String url() {
		return "https://www.ine.es/ss/Satellite?c=Page&cid=1259952026632&pagename=ProductosYServicios%2FPYSLayout";
	}

	@Override
	public boolean scheduled() {
		return false;
	}

	@Override
	public List<SeriesInfo> ingest() throws Exception {
		Path zip = http.download(URL, ".zip");
		Path dir = Files.createTempDirectory("secciones-");
		try {
			Path shp = extractShapefile(zip, dir);
			load(shp);
		}
		finally {
			Files.deleteIfExists(zip);
			try (Stream<Path> files = Files.walk(dir)) {
				files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
		return List.of();
	}

	/** Extracts only the shapefile parts, by bare file name, so archive paths can't escape {@code dir}. */
	static Path extractShapefile(Path zip, Path dir) throws IOException {
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
			for (ZipEntry e; (e = in.getNextEntry()) != null;) {
				String name = Path.of(e.getName()).getFileName().toString();
				if (!e.isDirectory() && name.matches(SHAPEFILE + "\\.(shp|shx|dbf|prj)")) {
					copy(in, dir.resolve(name));
				}
			}
		}
		Path shp = dir.resolve(SHAPEFILE + ".shp");
		if (!Files.exists(shp)) {
			throw new IOException("El ZIP del INE no contiene " + SHAPEFILE + ".shp");
		}
		return shp;
	}

	private static void copy(InputStream in, Path target) throws IOException {
		Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
	}

	void load(Path shp) {
		warehouse.loadSpatial();
		String path = shp.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
		warehouse.executeAll("""
				CREATE OR REPLACE TABLE census_sections AS
				SELECT CUSEC AS cusec,
				       CPRO::INTEGER AS cpro,
				       NMUN AS municipality,
				       ST_Area(geom) / 1e6 AS km2,
				       ST_ReducePrecision(
				           ST_Transform(ST_SimplifyPreserveTopology(geom, 10), 'EPSG:25830', 'EPSG:4326', always_xy := true),
				           0.00001) AS geom
				FROM ST_Read('%s', open_options = ['ENCODING=ISO-8859-1'])""".formatted(path));
	}

}
