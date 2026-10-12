package com.example.tasks.alert;

import com.example.tasks.series.SeriesRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleController {

	private static final int DEFAULT_LOOKBACK = 7;

	private final AlertRuleRepository rules;

	private final AlertRepository alerts;

	private final SeriesRepository series;

	private final AlertEvaluator evaluator;

	public AlertRuleController(AlertRuleRepository rules, AlertRepository alerts, SeriesRepository series,
			AlertEvaluator evaluator) {
		this.rules = rules;
		this.alerts = alerts;
		this.series = series;
		this.evaluator = evaluator;
	}

	@GetMapping
	public List<AlertRule> list() {
		return rules.findAllByOrderByCreatedAtAsc();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public AlertRule create(@Valid @RequestBody AlertRuleRequest request) {
		requireSeries(request.seriesCode());
		AlertRule rule = new AlertRule(request.name().strip(), request.seriesCode(), request.condition(),
				request.threshold(), request.severity(),
				request.lookback() != null ? request.lookback() : DEFAULT_LOOKBACK);
		rule.setEnabled(!Boolean.FALSE.equals(request.enabled()));
		rules.save(rule);
		evaluator.evaluate(rule);
		return rule;
	}

	@PutMapping("/{id}")
	@Transactional
	public AlertRule update(@PathVariable Long id, @Valid @RequestBody AlertRuleRequest request) {
		requireSeries(request.seriesCode());
		AlertRule rule = find(id);
		rule.setName(request.name().strip());
		rule.setSeriesCode(request.seriesCode());
		rule.setCondition(request.condition());
		rule.setThreshold(request.threshold());
		rule.setSeverity(request.severity());
		if (request.lookback() != null) {
			rule.setLookback(request.lookback());
		}
		if (request.enabled() != null) {
			rule.setEnabled(request.enabled());
		}
		evaluator.evaluate(rule);
		return rule;
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		AlertRule rule = find(id);
		alerts.deleteAllByRuleId(rule.getId());
		rules.delete(rule);
	}

	private void requireSeries(String code) {
		if (series.findByCode(code).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La serie " + code + " no existe");
		}
	}

	private AlertRule find(Long id) {
		return rules.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule " + id + " not found"));
	}

}
