package com.example.tasks.alert;

import com.example.tasks.alert.AlertRule.Condition;
import com.example.tasks.alert.AlertRule.Severity;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates a starter set of life-insurance alert rules on an empty database. */
@Component
class DefaultRulesSeeder implements ApplicationRunner {

	private final AlertRuleRepository rules;

	DefaultRulesSeeder(AlertRuleRepository rules) {
		this.rules = rules;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (rules.count() > 0) {
			return;
		}
		rules.saveAll(List.of(
				new AlertRule("Exceso de mortalidad nacional > 10 %", "momo.es.exceso7d.total", Condition.ABOVE, 10,
						Severity.CRITICAL, 14),
				new AlertRule("Exceso de mortalidad 85+ > 15 %", "momo.es.exceso7d.85", Condition.ABOVE, 15,
						Severity.WARNING, 14),
				new AlertRule("Muertes atribuibles al calor > 50/día", "momo.es.calor", Condition.ABOVE, 50,
						Severity.WARNING, 14),
				new AlertRule("Ola de calor en Sevilla (> 40 °C)", "meteo.tmax.sevilla", Condition.ABOVE, 40,
						Severity.WARNING, 14),
				new AlertRule("Ola de calor en Madrid (> 38 °C)", "meteo.tmax.madrid", Condition.ABOVE, 38,
						Severity.WARNING, 14),
				new AlertRule("Tipo a 10 años por debajo del 2,5 %", "ecb.yc.sr_10y", Condition.BELOW, 2.5,
						Severity.CRITICAL, 5),
				new AlertRule("Movimiento brusco del tipo a 30 años (±3 % diario)", "ecb.yc.sr_30y",
						Condition.CHANGE_PCT, 3, Severity.INFO, 5),
				new AlertRule("Longevidad: esperanza de vida a los 65 sube > 1 %", "eurostat.lifeexp.es.65",
						Condition.CHANGE_PCT, 1, Severity.INFO, 5),
				new AlertRule("Mercado Madrid: variación de familias > 2 %", "habits.prov.28.total",
						Condition.CHANGE_PCT, 2, Severity.INFO, 5)));
	}

}
