package com.example.tasks.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ingestion_runs")
public class IngestionRun {

	public enum Status {

		RUNNING, SUCCESS, FAILED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 40)
	private String source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Status status = Status.RUNNING;

	@Column(nullable = false)
	private Instant startedAt = Instant.now();

	private Instant finishedAt;

	private int seriesCount;

	private int alertsRaised;

	@Column(length = 1000)
	private String message;

	protected IngestionRun() {
	}

	public IngestionRun(String source) {
		this.source = source;
	}

	public void succeed(int seriesCount, int alertsRaised) {
		this.status = Status.SUCCESS;
		this.seriesCount = seriesCount;
		this.alertsRaised = alertsRaised;
		this.finishedAt = Instant.now();
	}

	public void fail(String message) {
		this.status = Status.FAILED;
		this.message = message == null ? null : message.substring(0, Math.min(message.length(), 1000));
		this.finishedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public int getSeriesCount() {
		return seriesCount;
	}

	public int getAlertsRaised() {
		return alertsRaised;
	}

	public String getMessage() {
		return message;
	}

}
