package com.example.tasks.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "alerts", uniqueConstraints = @UniqueConstraint(columnNames = { "ruleId", "pointDate" }))
public class Alert {

	public enum Status {

		OPEN, ACKNOWLEDGED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long ruleId;

	@Column(nullable = false, length = 150)
	private String ruleName;

	@Column(nullable = false, length = 120)
	private String seriesCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private AlertRule.Severity severity;

	@Column(nullable = false)
	private LocalDate pointDate;

	@Column(name = "val", nullable = false)
	private double value;

	@Column(nullable = false, length = 500)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private Status status = Status.OPEN;

	@Column(nullable = false, updatable = false)
	private Instant triggeredAt = Instant.now();

	private Instant acknowledgedAt;

	protected Alert() {
	}

	public Alert(AlertRule rule, LocalDate pointDate, double value, String message) {
		this.ruleId = rule.getId();
		this.ruleName = rule.getName();
		this.seriesCode = rule.getSeriesCode();
		this.severity = rule.getSeverity();
		this.pointDate = pointDate;
		this.value = value;
		this.message = message;
	}

	public void acknowledge() {
		if (status == Status.OPEN) {
			status = Status.ACKNOWLEDGED;
			acknowledgedAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public Long getRuleId() {
		return ruleId;
	}

	public String getRuleName() {
		return ruleName;
	}

	public String getSeriesCode() {
		return seriesCode;
	}

	public AlertRule.Severity getSeverity() {
		return severity;
	}

	public LocalDate getPointDate() {
		return pointDate;
	}

	public double getValue() {
		return value;
	}

	public String getMessage() {
		return message;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getTriggeredAt() {
		return triggeredAt;
	}

	public Instant getAcknowledgedAt() {
		return acknowledgedAt;
	}

}
