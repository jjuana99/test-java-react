package com.example.tasks.warehouse;

import jakarta.annotation.PreDestroy;
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

	private final DuckDBConnection root;

	public Warehouse(@Value("${app.warehouse.url:jdbc:duckdb:}") String url) throws SQLException {
		this.root = (DuckDBConnection) DriverManager.getConnection(url);
		execute("""
				CREATE TABLE IF NOT EXISTS points (
					series_code VARCHAR NOT NULL,
					date DATE NOT NULL,
					value DOUBLE NOT NULL
				)""");
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
