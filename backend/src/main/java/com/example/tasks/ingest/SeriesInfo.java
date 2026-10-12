package com.example.tasks.ingest;

/** Catalog metadata for a series that a connector has written to the warehouse. */
public record SeriesInfo(String code, String name, String unit, String frequency, String description) {
}
