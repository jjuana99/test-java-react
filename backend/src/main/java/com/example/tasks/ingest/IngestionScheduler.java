package com.example.tasks.ingest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process daily refresh. Disabled where instances scale to zero (Cloud Run), which use
 * Cloud Scheduler calling {@code POST /api/sources/ingest-scheduled} instead.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.ingestion.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class IngestionScheduler {

	private final IngestionService ingestion;

	IngestionScheduler(IngestionService ingestion) {
		this.ingestion = ingestion;
	}

	@Scheduled(cron = "${app.ingestion.cron:0 0 6 * * *}", zone = "Europe/Madrid")
	void refreshAll() {
		ingestion.triggerScheduled();
	}

}
