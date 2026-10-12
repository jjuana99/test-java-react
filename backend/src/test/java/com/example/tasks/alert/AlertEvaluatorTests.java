package com.example.tasks.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tasks.alert.AlertRule.Condition;
import com.example.tasks.alert.AlertRule.Severity;
import com.example.tasks.warehouse.Point;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AlertEvaluatorTests {

	private static final LocalDate D1 = LocalDate.of(2026, 10, 1);

	private static final LocalDate D2 = LocalDate.of(2026, 10, 2);

	private static AlertRule rule(Condition condition, double threshold) {
		return new AlertRule("r", "s", condition, threshold, Severity.INFO, 7);
	}

	@Test
	void aboveAndBelowAreStrict() {
		assertThat(AlertEvaluator.check(rule(Condition.ABOVE, 10), new Point(D2, 10.5), null)).isPresent();
		assertThat(AlertEvaluator.check(rule(Condition.ABOVE, 10), new Point(D2, 10), null)).isEmpty();
		assertThat(AlertEvaluator.check(rule(Condition.BELOW, 2.5), new Point(D2, 2.4), null)).isPresent();
		assertThat(AlertEvaluator.check(rule(Condition.BELOW, 2.5), new Point(D2, 2.5), null)).isEmpty();
	}

	@Test
	void changePctUsesAbsoluteVariationAgainstPreviousPoint() {
		AlertRule r = rule(Condition.CHANGE_PCT, 3);
		assertThat(AlertEvaluator.check(r, new Point(D2, 96), new Point(D1, 100))).isPresent();
		assertThat(AlertEvaluator.check(r, new Point(D2, 104), new Point(D1, 100))).isPresent();
		assertThat(AlertEvaluator.check(r, new Point(D2, 102), new Point(D1, 100))).isEmpty();
		assertThat(AlertEvaluator.check(r, new Point(D2, 102), null)).isEmpty();
	}

}
