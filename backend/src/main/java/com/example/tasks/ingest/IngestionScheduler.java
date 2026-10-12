package com.example.tasks.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.ingestion.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class IngestionScheduler {

	private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

	private final IngestionService ingestion;

	IngestionScheduler(IngestionService ingestion) {
		this.ingestion = ingestion;
	}

	@Scheduled(cron = "${app.ingestion.cron:0 0 6 * * *}", zone = "Europe/Madrid")
	void refreshAll() {
		for (DataConnector connector : ingestion.connectors()) {
			if (connector.scheduled()) {
				try {
					ingestion.trigger(connector.id());
				}
				catch (ResponseStatusException e) {
					log.info("Se omite {}: {}", connector.id(), e.getReason());
				}
			}
		}
	}

}
