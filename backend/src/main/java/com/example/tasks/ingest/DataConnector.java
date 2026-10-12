package com.example.tasks.ingest;

import java.util.List;

/**
 * A public data source. Implementations land their data in the warehouse and report
 * which series they updated.
 */
public interface DataConnector {

	String id();

	String name();

	String description();

	String url();

	/** Whether the daily scheduler refreshes this source automatically. */
	default boolean scheduled() {
		return true;
	}

	List<SeriesInfo> ingest() throws Exception;

}
