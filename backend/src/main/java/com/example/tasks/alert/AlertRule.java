package com.example.tasks.alert;

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
@Table(name = "alert_rules")
public class AlertRule {

	public enum Condition {

		/** Value strictly above the threshold. */
		ABOVE,
		/** Value strictly below the threshold. */
		BELOW,
		/** Absolute percentage change versus the previous point above the threshold. */
		CHANGE_PCT

	}

	public enum Severity {

		INFO, WARNING, CRITICAL

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 120)
	private String seriesCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "rule_condition", nullable = false, length = 12)
	private Condition condition;

	@Column(nullable = false)
	private double threshold;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Severity severity;

	/** How many of the most recent points are checked on each evaluation. */
	@Column(nullable = false)
	private int lookback;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected AlertRule() {
	}

	public AlertRule(String name, String seriesCode, Condition condition, double threshold, Severity severity,
			int lookback) {
		this.name = name;
		this.seriesCode = seriesCode;
		this.condition = condition;
		this.threshold = threshold;
		this.severity = severity;
		this.lookback = lookback;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSeriesCode() {
		return seriesCode;
	}

	public void setSeriesCode(String seriesCode) {
		this.seriesCode = seriesCode;
	}

	public Condition getCondition() {
		return condition;
	}

	public void setCondition(Condition condition) {
		this.condition = condition;
	}

	public double getThreshold() {
		return threshold;
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	public Severity getSeverity() {
		return severity;
	}

	public void setSeverity(Severity severity) {
		this.severity = severity;
	}

	public int getLookback() {
		return lookback;
	}

	public void setLookback(int lookback) {
		this.lookback = lookback;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
