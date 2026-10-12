package com.example.tasks.series;

import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/series")
public class SeriesController {

	private final SeriesRepository series;

	private final Warehouse warehouse;

	public SeriesController(SeriesRepository series, Warehouse warehouse) {
		this.series = series;
		this.warehouse = warehouse;
	}

	@GetMapping
	public List<Series> list(@RequestParam(required = false) String source) {
		return source == null ? series.findAllByOrderBySourceAscCodeAsc()
				: series.findAllBySourceOrderByCodeAsc(source);
	}

	@GetMapping("/{code}/points")
	public List<Point> points(@PathVariable String code,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
		if (series.findByCode(code).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Series " + code + " not found");
		}
		return warehouse.points(code, from != null ? from : LocalDate.of(1900, 1, 1));
	}

}
