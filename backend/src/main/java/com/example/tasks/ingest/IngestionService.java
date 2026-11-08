package com.example.tasks.ingest;

import com.example.tasks.alert.AlertEvaluator;
import com.example.tasks.series.Series;
import com.example.tasks.series.SeriesRepository;
import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs connectors one at a time on a background thread, refreshes the series catalog
 * and evaluates the alert rules of every updated series.
 */
@Service
public class IngestionService implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private final Map<String, DataConnector> connectors = new LinkedHashMap<>();

	private final IngestionRunRepository runs;

	private final SeriesRepository series;

	private final Warehouse warehouse;

	private final AlertEvaluator evaluator;

	private final TransactionTemplate tx;

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "ingestion"));

	public IngestionService(List<DataConnector> connectors, IngestionRunRepository runs, SeriesRepository series,
			Warehouse warehouse, AlertEvaluator evaluator, TransactionTemplate tx) {
		connectors.forEach(c -> this.connectors.put(c.id(), c));
		this.runs = runs;
		this.series = series;
		this.warehouse = warehouse;
		this.evaluator = evaluator;
		this.tx = tx;
	}

	/** Runs left RUNNING by a previous process can never finish. */
	@Override
	public void run(ApplicationArguments args) {
		tx.executeWithoutResult(s -> runs.findAllByStatus(IngestionRun.Status.RUNNING)
			.forEach(r -> r.fail("Interrumpida por un reinicio del servidor")));
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	public List<DataConnector> connectors() {
		return List.copyOf(connectors.values());
	}

	/** Queues an ingestion and returns immediately with the RUNNING run. */
	public synchronized IngestionRun trigger(String connectorId) {
		DataConnector connector = connectors.get(connectorId);
		if (connector == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source " + connectorId + " not found");
		}
		boolean busy = runs.findFirstBySourceOrderByStartedAtDesc(connectorId)
			.map(r -> r.getStatus() == IngestionRun.Status.RUNNING)
			.orElse(false);
		if (busy) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una ingesta en curso para " + connectorId);
		}
		IngestionRun run = begin(connectorId);
		executor.submit(() -> execute(connector, run.getId()));
		return run;
	}

	/** Queues every connector refreshed automatically; sources already running are skipped. */
	public List<IngestionRun> triggerScheduled() {
		List<IngestionRun> started = new ArrayList<>();
		for (DataConnector connector : connectors()) {
			if (connector.scheduled()) {
				try {
					started.add(trigger(connector.id()));
				}
				catch (ResponseStatusException e) {
					log.info("Se omite {}: {}", connector.id(), e.getReason());
				}
			}
		}
		return started;
	}

	IngestionRun begin(String source) {
		return runs.save(new IngestionRun(source));
	}

	void execute(DataConnector connector, Long runId) {
		long start = System.currentTimeMillis();
		try {
			List<SeriesInfo> infos = connector.ingest();
			int alerts = tx.execute(s -> {
				infos.forEach(info -> upsertSeries(connector.id(), info));
				return evaluator.evaluateSeries(infos.stream().map(SeriesInfo::code).toList());
			});
			finish(runId, run -> run.succeed(infos.size(), alerts));
			log.info("Ingesta {} completada: {} series, {} alertas en {} ms", connector.id(), infos.size(), alerts,
					System.currentTimeMillis() - start);
			try {
				warehouse.snapshot();
			}
			catch (RuntimeException e) {
				log.warn("No se pudo guardar la copia del almacén tras la ingesta {}", connector.id(), e);
			}
		}
		catch (Exception e) {
			log.warn("Ingesta {} fallida", connector.id(), e);
			finish(runId, run -> run.fail(e.getClass().getSimpleName() + ": " + e.getMessage()));
		}
	}

	private void upsertSeries(String source, SeriesInfo info) {
		Series s = series.findByCode(info.code()).orElseGet(() -> new Series(info.code(), source));
		s.setName(info.name());
		s.setUnit(info.unit());
		s.setFrequency(info.frequency());
		s.setDescription(info.description());
		List<Point> last = warehouse.latestPoints(info.code(), 1);
		s.setLast(last.isEmpty() ? null : last.getFirst().date(), last.isEmpty() ? null : last.getFirst().value());
		s.setUpdatedAt(Instant.now());
		series.save(s);
	}

	private void finish(Long runId, java.util.function.Consumer<IngestionRun> update) {
		tx.executeWithoutResult(s -> runs.findById(runId).ifPresent(update));
	}

}
