package com.example.tasks.warehouse;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Analytical store backed by an embedded DuckDB database. Holds the raw landed datasets
 * (millions of rows) and every time-series point; the operational database only keeps
 * the series catalog, alert rules, alerts and ingestion runs.
 */
@Component
public class Warehouse {

	@FunctionalInterface
	public interface RowMapper<T> {

		T map(ResultSet rs) throws SQLException;

	}

	@FunctionalInterface
	public interface Work {

		void run(Connection connection) throws SQLException;

	}

	private static final Logger log = LoggerFactory.getLogger(Warehouse.class);

	private final DuckDBConnection root;

	/** Local database file, or null for an in-memory database. */
	private final Path localFile;

	/** Durable copy (e.g. a Cloud Storage mount), or null when not configured. */
	private final Path snapshot;

	public Warehouse(String url) throws SQLException {
		this(url, "");
	}

	/**
	 * With {@code snapshot} set, the database runs on a local file (fast, supports DuckDB's
	 * random writes) restored from the snapshot at startup and copied back by
	 * {@link #snapshot()}. Used where the local disk is ephemeral, like Cloud Run.
	 */
	@Autowired
	public Warehouse(@Value("${app.warehouse.url:jdbc:duckdb:}") String url,
			@Value("${app.warehouse.snapshot:}") String snapshot) throws SQLException {
		String file = url.substring("jdbc:duckdb:".length());
		this.localFile = file.isBlank() ? null : Path.of(file);
		this.snapshot = snapshot.isBlank() ? null : Path.of(snapshot);
		restore();
		this.root = (DuckDBConnection) DriverManager.getConnection(url);
		execute("""
				CREATE TABLE IF NOT EXISTS points (
					series_code VARCHAR NOT NULL,
					date DATE NOT NULL,
					value DOUBLE NOT NULL
				)""");
	}

	private void restore() {
		if (localFile == null) {
			return;
		}
		try {
			Files.createDirectories(localFile.toAbsolutePath().getParent());
			if (snapshot == null || Files.exists(localFile) || !Files.exists(snapshot)) {
				return;
			}
			Files.copy(snapshot, localFile);
			log.info("Almacén restaurado desde {} ({} MB)", snapshot, Files.size(localFile) / 1_000_000);
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo restaurar el almacén desde " + snapshot, e);
		}
	}

	/**
	 * Has DuckDB write a consistent copy of the database to a new local file (the open file
	 * may be locked) and then moves that closed file to the snapshot location.
	 */
	public synchronized void snapshot() {
		if (snapshot == null || localFile == null) {
			return;
		}
		if (tableExists("census_sections")) {
			loadSpatial(); // GEOMETRY columns need the extension to be copied
		}
		Path copy = localFile.toAbsolutePath().resolveSibling("snapshot-copy.duckdb");
		try {
			Files.deleteIfExists(copy);
			String current = query("SELECT current_database()", rs -> rs.getString(1)).getFirst();
			autoCommit("ATTACH '%s' AS snap".formatted(copy.toString().replace('\\', '/').replace("'", "''")),
					"COPY FROM DATABASE \"%s\" TO snap".formatted(current.replace("\"", "\"\"")), "DETACH snap");
			Path tmp = snapshot.resolveSibling(snapshot.getFileName() + ".tmp");
			Files.copy(copy, tmp, StandardCopyOption.REPLACE_EXISTING);
			Files.move(tmp, snapshot, StandardCopyOption.REPLACE_EXISTING);
			log.info("Copia del almacén guardada en {} ({} MB)", snapshot, Files.size(snapshot) / 1_000_000);
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo guardar la copia del almacén en " + snapshot, e);
		}
		finally {
			try {
				Files.deleteIfExists(copy);
			}
			catch (IOException e) {
				log.warn("No se pudo borrar {}", copy, e);
			}
		}
	}

	/** Statements that cannot run inside a transaction, such as ATTACH. */
	private void autoCommit(String... statements) {
		try (Connection c = root.duplicate(); Statement st = c.createStatement()) {
			for (String sql : statements) {
				st.execute(sql);
			}
		}
		catch (SQLException e) {
			throw new WarehouseException(e);
		}
	}

	@PreDestroy
	void close() throws SQLException {
		root.close();
	}

	private volatile boolean spatialLoaded;

	/**
	 * Loads DuckDB's spatial extension, downloading it the first time (it is then cached
	 * under ~/.duckdb/extensions).
	 */
	public synchronized void loadSpatial() {
		if (!spatialLoaded) {
			executeAll("INSTALL spatial", "LOAD spatial");
			spatialLoaded = true;
		}
	}

	public boolean tableExists(String table) {
		return !query("SELECT 1 FROM duckdb_tables() WHERE table_name = ?", rs -> 1, table).isEmpty();
	}

	public void execute(String sql, Object... params) {
		inTransaction(c -> {
			try (PreparedStatement ps = c.prepareStatement(sql)) {
				bind(ps, params);
				ps.execute();
			}
		});
	}

	public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
		try (Connection c = root.duplicate(); PreparedStatement ps = c.prepareStatement(sql)) {
			bind(ps, params);
			List<T> rows = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					rows.add(mapper.map(rs));
				}
			}
			return rows;
		}
		catch (SQLException e) {
			throw new WarehouseException(e);
		}
	}

	public void inTransaction(Work work) {
		try (Connection c = root.duplicate()) {
			c.setAutoCommit(false);
			try {
				work.run(c);
				c.commit();
			}
			catch (SQLException | RuntimeException e) {
				c.rollback();
				throw e;
			}
		}
		catch (SQLException e) {
			throw new WarehouseException(e);
		}
	}

	/** Runs several statements atomically. */
	public void executeAll(String... statements) {
		inTransaction(c -> {
			try (Statement st = c.createStatement()) {
				for (String sql : statements) {
					st.execute(sql);
				}
			}
		});
	}

	/** Replaces the points of a series inside the date range covered by {@code newPoints}. */
	public void replacePoints(String seriesCode, List<Point> newPoints) {
		if (newPoints.isEmpty()) {
			return;
		}
		LocalDate min = newPoints.stream().map(Point::date).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate max = newPoints.stream().map(Point::date).max(Comparator.naturalOrder()).orElseThrow();
		inTransaction(c -> {
			try (PreparedStatement del = c
				.prepareStatement("DELETE FROM points WHERE series_code = ? AND date BETWEEN ? AND ?")) {
				bind(del, seriesCode, min, max);
				del.executeUpdate();
			}
			try (PreparedStatement ins = c.prepareStatement("INSERT INTO points VALUES (?, ?, ?)")) {
				for (Point p : newPoints) {
					bind(ins, seriesCode, p.date(), p.value());
					ins.addBatch();
				}
				ins.executeBatch();
			}
		});
	}

	public List<Point> points(String seriesCode, LocalDate from) {
		return query("SELECT date, value FROM points WHERE series_code = ? AND date >= ? ORDER BY date",
				Warehouse::point, seriesCode, from);
	}

	/** Most recent {@code limit} points, newest first. */
	public List<Point> latestPoints(String seriesCode, int limit) {
		return query("SELECT date, value FROM points WHERE series_code = ? ORDER BY date DESC LIMIT ?",
				Warehouse::point, seriesCode, limit);
	}

	private static Point point(ResultSet rs) throws SQLException {
		return new Point(rs.getDate(1).toLocalDate(), rs.getDouble(2));
	}

	private static void bind(PreparedStatement ps, Object... params) throws SQLException {
		for (int i = 0; i < params.length; i++) {
			Object p = params[i];
			ps.setObject(i + 1, p instanceof LocalDate d ? Date.valueOf(d) : p);
		}
	}

}
