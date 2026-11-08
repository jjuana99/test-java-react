package com.example.tasks.ingest;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

	public record SourceView(String id, String name, String description, String url, boolean scheduled,
			IngestionRun lastRun) {
	}

	private final IngestionService ingestion;

	private final IngestionRunRepository runs;

	public SourceController(IngestionService ingestion, IngestionRunRepository runs) {
		this.ingestion = ingestion;
		this.runs = runs;
	}

	@GetMapping
	public List<SourceView> list() {
		return ingestion.connectors()
			.stream()
			.map(c -> new SourceView(c.id(), c.name(), c.description(), c.url(), c.scheduled(),
					runs.findFirstBySourceOrderByStartedAtDesc(c.id()).orElse(null)))
			.toList();
	}

	/** Refreshes every scheduled source; called daily by Cloud Scheduler. */
	@PostMapping("/ingest-scheduled")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public List<IngestionRun> ingestScheduled() {
		return ingestion.triggerScheduled();
	}

	@PostMapping("/{id}/ingest")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public IngestionRun ingest(@PathVariable String id) {
		return ingestion.trigger(id);
	}

	@GetMapping("/runs")
	public List<IngestionRun> runs() {
		return runs.findTop50ByOrderByStartedAtDesc();
	}

}
