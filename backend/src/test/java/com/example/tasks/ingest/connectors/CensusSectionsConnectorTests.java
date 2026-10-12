package com.example.tasks.ingest.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CensusSectionsConnectorTests {

	@TempDir
	Path tmp;

	@Test
	void extractsOnlyShapefilePartsByBareName() throws Exception {
		Path zip = tmp.resolve("carto.zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
			for (String name : new String[] { "carpeta/SECC_CPV_E_20111101_01_R_INE.shp",
					"SECC_CPV_E_20111101_01_R_INE.dbf", "../../SECC_CPV_E_20111101_01_R_INE.shx", "leeme.txt" }) {
				out.putNextEntry(new ZipEntry(name));
				out.write(name.getBytes());
				out.closeEntry();
			}
		}
		Path dir = Files.createDirectory(tmp.resolve("out"));

		Path shp = CensusSectionsConnector.extractShapefile(zip, dir);

		assertThat(shp).isEqualTo(dir.resolve("SECC_CPV_E_20111101_01_R_INE.shp"));
		try (var files = Files.list(dir)) {
			assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder(
					"SECC_CPV_E_20111101_01_R_INE.shp", "SECC_CPV_E_20111101_01_R_INE.dbf",
					"SECC_CPV_E_20111101_01_R_INE.shx");
		}
		assertThat(tmp.getParent().resolve("SECC_CPV_E_20111101_01_R_INE.shx")).doesNotExist();
	}

}
