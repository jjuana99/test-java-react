package com.example.tasks.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WarehouseSnapshotTests {

	@TempDir
	Path tmp;

	@Test
	void snapshotSurvivesLosingTheLocalDisk() throws Exception {
		Path bucket = Files.createDirectory(tmp.resolve("bucket"));
		Path snapshot = bucket.resolve("warehouse.duckdb");
		// the local directory does not exist yet, as on a fresh Cloud Run instance
		String firstDisk = "jdbc:duckdb:" + tmp.resolve("disk1/warehouse.duckdb").toString().replace('\\', '/');

		Warehouse first = new Warehouse(firstDisk, snapshot.toString());
		first.replacePoints("s", List.of(new Point(LocalDate.of(2026, 1, 1), 42)));
		first.snapshot();
		first.close();
		assertThat(snapshot).exists();

		// a new instance with an empty local disk restores from the snapshot
		String secondDisk = "jdbc:duckdb:" + tmp.resolve("disk2/warehouse.duckdb").toString().replace('\\', '/');
		Warehouse second = new Warehouse(secondDisk, snapshot.toString());
		assertThat(second.points("s", LocalDate.of(2000, 1, 1))).containsExactly(new Point(LocalDate.of(2026, 1, 1), 42));
		second.close();
	}

	@Test
	void inMemoryWarehouseIgnoresSnapshot() throws Exception {
		Warehouse w = new Warehouse("jdbc:duckdb:", tmp.resolve("x.duckdb").toString());
		w.snapshot();
		assertThat(tmp.resolve("x.duckdb")).doesNotExist();
		w.close();
	}

}
