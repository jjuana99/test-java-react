package com.example.tasks.alert;

import com.example.tasks.warehouse.Point;
import com.example.tasks.warehouse.Warehouse;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks alert rules against the latest points of their series and records new alerts. */
@Service
public class AlertEvaluator {

	private final AlertRuleRepository rules;

	private final AlertRepository alerts;

	private final Warehouse warehouse;

	public AlertEvaluator(AlertRuleRepository rules, AlertRepository alerts, Warehouse warehouse) {
		this.rules = rules;
		this.alerts = alerts;
		this.warehouse = warehouse;
	}

	@Transactional
	public int evaluateSeries(Collection<String> seriesCodes) {
		if (seriesCodes.isEmpty()) {
			return 0;
		}
		return rules.findAllByEnabledTrueAndSeriesCodeIn(seriesCodes).stream().mapToInt(this::evaluate).sum();
	}

	/** Returns the number of new alerts raised. */
	@Transactional
	public int evaluate(AlertRule rule) {
		if (!rule.isEnabled()) {
			return 0;
		}
		// one extra point so the oldest checked point still has a predecessor for CHANGE_PCT
		List<Point> newestFirst = warehouse.latestPoints(rule.getSeriesCode(), rule.getLookback() + 1);
		int raised = 0;
		for (int i = 0; i < Math.min(rule.getLookback(), newestFirst.size()); i++) {
			Point current = newestFirst.get(i);
			Point previous = i + 1 < newestFirst.size() ? newestFirst.get(i + 1) : null;
			Optional<String> message = check(rule, current, previous);
			if (message.isPresent() && !alerts.existsByRuleIdAndPointDate(rule.getId(), current.date())) {
				alerts.save(new Alert(rule, current.date(), current.value(), message.get()));
				raised++;
			}
		}
		return raised;
	}

	static Optional<String> check(AlertRule rule, Point current, Point previous) {
		double t = rule.getThreshold();
		double v = current.value();
		return switch (rule.getCondition()) {
			case ABOVE -> v > t ? Optional.of("Valor %s supera el umbral %s".formatted(fmt(v), fmt(t)))
					: Optional.empty();
			case BELOW -> v < t ? Optional.of("Valor %s por debajo del umbral %s".formatted(fmt(v), fmt(t)))
					: Optional.empty();
			case CHANGE_PCT -> {
				if (previous == null || previous.value() == 0) {
					yield Optional.empty();
				}
				double change = 100 * (v - previous.value()) / Math.abs(previous.value());
				yield Math.abs(change) > t
						? Optional.of("Variación de %s%% respecto a %s (%s → %s), umbral ±%s%%".formatted(fmt(change),
								previous.date(), fmt(previous.value()), fmt(v), fmt(t)))
						: Optional.empty();
			}
		};
	}

	private static String fmt(double d) {
		return String.format(Locale.forLanguageTag("es-ES"), "%,.2f", d);
	}

}
