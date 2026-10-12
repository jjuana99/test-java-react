package com.example.tasks.series;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "series")
public class Series {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 120)
	private String code;

	@Column(nullable = false, length = 40)
	private String source;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 40)
	private String unit;

	@Column(length = 20)
	private String frequency;

	@Column(length = 500)
	private String description;

	private LocalDate lastDate;

	private Double lastValue;

	private Instant updatedAt;

	protected Series() {
	}

	public Series(String code, String source) {
		this.code = code;
		this.source = source;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getSource() {
		return source;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getFrequency() {
		return frequency;
	}

	public void setFrequency(String frequency) {
		this.frequency = frequency;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getLastDate() {
		return lastDate;
	}

	public Double getLastValue() {
		return lastValue;
	}

	public void setLast(LocalDate lastDate, Double lastValue) {
		this.lastDate = lastDate;
		this.lastValue = lastValue;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
