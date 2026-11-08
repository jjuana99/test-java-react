package com.example.tasks.retirement;

import com.example.tasks.retirement.PlanRequest.Employment;
import com.example.tasks.retirement.PlanRequest.RiskProfile;
import com.example.tasks.retirement.RetirementPlan.Pension;
import com.example.tasks.retirement.RetirementPlan.ProjectionPoint;
import com.example.tasks.retirement.RetirementPlan.Recommendation;
import com.example.tasks.retirement.RetirementPlan.Scenario;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds a savings plan: estimates the public pension, sizes the income gap against the
 * target, prices it over the expected retirement horizon and solves the monthly saving
 * needed to fund it. Pure calculation, no I/O.
 */
@Component
public class RetirementPlanner {

	/** Extra years planned beyond life expectancy to cover longevity risk. */
	static final int LONGEVITY_BUFFER = 5;

	static final int MAX_RETIREMENT_AGE = 72;

	/** Cut in the public pension used for the prudence scenario. */
	static final double STRESS = 0.20;

	/** Real return premium over the safe rate assumed for each risk profile. */
	static double premium(RiskProfile profile) {
		return switch (profile) {
			case CONSERVATIVE -> 0;
			case BALANCED -> 0.015;
			case DYNAMIC -> 0.03;
		};
	}

	public RetirementPlan plan(PlanRequest req, MarketAssumptions market, LocalDate today) {
		List<String> warnings = new ArrayList<>();
		double age = ChronoUnit.DAYS.between(req.birthDate(), today) / 365.25;

		double yearsAt65 = contributionYearsAt(req, age, 65);
		int ordinaryAge = PensionRules.ordinaryAge(yearsAt65);
		int retirementAge = ordinaryAge;
		if (req.retirementAge() != null) {
			if (req.retirementAge() < ordinaryAge) {
				warnings.add("La jubilación anticipada (antes de los %d) aplica coeficientes reductores que este simulador no calcula; se usa la edad ordinaria."
					.formatted(ordinaryAge));
			}
			else {
				retirementAge = Math.min(req.retirementAge(), MAX_RETIREMENT_AGE);
			}
		}
		retirementAge = Math.max(retirementAge, (int) Math.ceil(age));
		double yearsToRetirement = Math.max(0, retirementAge - age);

		Pension pension = pension(req, age, retirementAge, ordinaryAge);
		double accumulation = market.safeRealRate() + premium(req.riskProfile());
		double decumulation = market.safeRealRate();
		double finalSalary = req.salaryIn(yearsToRetirement);
		double target = finalSalary * req.replacement();
		double gap = Math.max(0, target - pension.annual());
		double lifeExpectancy = market.lifeExpectancyAt(retirementAge);
		int horizon = (int) Math.ceil(lifeExpectancy) + LONGEVITY_BUFFER;
		double capital = annuityDue(gap, decumulation, horizon);
		double savingsAtRetirement = req.savings() * Math.pow(1 + accumulation, yearsToRetirement);
		double monthly = monthlySaving(capital - savingsAtRetirement, accumulation, yearsToRetirement);
		boolean onTrack = capital <= savingsAtRetirement;
		// stress test: public pension STRESS lower than under current law
		double stressedCapital = annuityDue(Math.max(0, target - pension.annual() * (1 - STRESS)), decumulation,
				horizon);
		double stressedMonthly = monthlySaving(stressedCapital - savingsAtRetirement, accumulation,
				yearsToRetirement);

		// what the capital buys as a life annuity priced at the long rate over life expectancy
		double annuity = gap == 0 ? 0
				: capital / annuityDue(1, market.longRealRate(), (int) Math.ceil(lifeExpectancy)) / 12;

		List<Scenario> scenarios = new ArrayList<>();
		for (RiskProfile profile : RiskProfile.values()) {
			for (int extra = 0; extra <= 2; extra++) {
				int r = Math.min(retirementAge + extra, MAX_RETIREMENT_AGE);
				if (extra > 0 && r == retirementAge) {
					continue;
				}
				Pension p = pension(req, age, r, ordinaryAge);
				double rate = market.safeRealRate() + premium(profile);
				double cap = annuityDue(Math.max(0, req.salaryIn(r - age) * req.replacement() - p.annual()),
						decumulation,
						(int) Math.ceil(market.lifeExpectancyAt(r)) + LONGEVITY_BUFFER);
				double years = Math.max(0, r - age);
				scenarios.add(new Scenario(profile, r,
						round(monthlySaving(cap - req.savings() * Math.pow(1 + rate, years), rate, years)),
						round(p.monthly())));
			}
		}

		addWarnings(req, pension, age, retirementAge, warnings);
		return new RetirementPlan(round(age), ordinaryAge, retirementAge, round(yearsToRetirement), pension,
				round(finalSalary), round(target), round(gap), round(lifeExpectancy), horizon, round(capital),
				round(savingsAtRetirement), round(monthly), onTrack, round(stressedMonthly),
				round(Math.max(0, target - pension.annual() * (1 - STRESS))), round(stressedCapital),
				new RetirementPlan.Rates(round4(accumulation), round4(decumulation), round4(market.safeNominalRate()),
						MarketAssumptions.INFLATION, req.wageGrowth(), market.ratesDate(), market.lifeTableYear()),
				round(annuity), scenarios,
				projection(req, age, retirementAge, horizon, monthly, gap, accumulation, decumulation, today),
				recommendations(req, pension, monthly, stressedMonthly, gap, capital, annuity, yearsToRetirement,
						scenarios, retirementAge),
				warnings, null, null);
	}

	static double contributionYearsAt(PlanRequest req, double age, double atAge) {
		return req.contributionYears() + (req.contributing() ? Math.max(0, atAge - age) : 0);
	}

	static Pension pension(PlanRequest req, double age, int retirementAge, int ordinaryAge) {
		double years = contributionYearsAt(req, age, retirementAge);
		double pct = PensionRules.percentage(years);
		double bonus = PensionRules.DELAY_BONUS_PER_YEAR * Math.max(0, retirementAge - ordinaryAge);
		// average monthly contribution base over the 25 years before retiring (real terms, capped each year)
		double yearsToRetirement = Math.max(0, retirementAge - age);
		double sum = 0;
		for (int k = 1; k <= PensionRules.REGULATORY_YEARS; k++) {
			sum += Math.min(req.salaryIn(yearsToRetirement - k + 0.5) / 12, PensionRules.MAX_CONTRIBUTION_BASE);
		}
		double base = sum / PensionRules.REGULATORY_YEARS;
		// regulatory base: 300 monthly bases divided by 350, i.e. spread over 14 payments
		double regulatory = base * 12 / PensionRules.PENSION_PAYMENTS;
		double raw = regulatory * pct / 100 * (1 + bonus);
		double monthly = Math.min(raw, PensionRules.MAX_PENSION);
		return new Pension(pct > 0, round(years), round(pct), round(bonus * 100), round(base), round(regulatory),
				round(monthly), round(monthly * PensionRules.PENSION_PAYMENTS), raw > PensionRules.MAX_PENSION);
	}

	/** Present value of {@code years} yearly payments made at the start of each year. */
	static double annuityDue(double payment, double rate, int years) {
		if (payment <= 0 || years <= 0) {
			return 0;
		}
		if (Math.abs(rate) < 1e-9) {
			return payment * years;
		}
		return payment * (1 - Math.pow(1 + rate, -years)) / rate * (1 + rate);
	}

	/** Monthly contribution whose future value after {@code years} equals {@code amount}. */
	static double monthlySaving(double amount, double annualRate, double years) {
		if (amount <= 0) {
			return 0;
		}
		int months = (int) Math.round(years * 12);
		if (months <= 0) {
			return amount; // nothing left to accumulate: the whole shortfall is needed now
		}
		double i = Math.pow(1 + annualRate, 1.0 / 12) - 1;
		return Math.abs(i) < 1e-12 ? amount / months : amount * i / (Math.pow(1 + i, months) - 1);
	}

	private static List<ProjectionPoint> projection(PlanRequest req, double age, int retirementAge, int horizon,
			double monthly, double gap, double accumulation, double decumulation, LocalDate today) {
		List<ProjectionPoint> points = new ArrayList<>();
		double balance = req.savings();
		double iAcc = Math.pow(1 + accumulation, 1.0 / 12) - 1;
		int startAge = (int) Math.floor(age);
		int year = today.getYear();
		points.add(new ProjectionPoint(startAge, year, round(balance)));
		for (int a = startAge + 1; a <= retirementAge + horizon; a++) {
			year++;
			if (a <= retirementAge) {
				for (int m = 0; m < 12; m++) {
					balance = balance * (1 + iAcc) + monthly;
				}
			}
			else {
				balance = (balance - gap) * (1 + decumulation);
			}
			points.add(new ProjectionPoint(a, year, round(Math.max(balance, 0))));
		}
		return points;
	}

	private static List<Recommendation> recommendations(PlanRequest req, Pension pension, double monthly,
			double stressedMonthly, double gap, double capital, double annuity, double yearsToRetirement,
			List<Scenario> scenarios, int retirementAge) {
		List<Recommendation> recs = new ArrayList<>();
		double remaining = monthly * 12;
		boolean taxable = req.grossAnnualSalary() > 0
				&& (req.employment() == Employment.EMPLOYEE || req.employment() == Employment.SELF_EMPLOYED
						|| req.employment() == Employment.CIVIL_SERVANT);

		if (remaining > 0 && taxable) {
			double limit = PensionRules.PENSION_PLAN_LIMIT
					+ (req.employment() == Employment.SELF_EMPLOYED ? PensionRules.SELF_EMPLOYED_PLAN_EXTRA : 0);
			double amount = Math.min(remaining, limit);
			double saving = amount * PensionRules.marginalTaxRate(req.grossAnnualSalary());
			recs.add(new Recommendation("PENSION_PLAN", "Plan de pensiones",
					"Aporta %s €/año (límite deducible %s €%s). Reduce tu base del IRPF: ahorro fiscal aproximado de %s €/año. Tributa como rendimiento del trabajo al rescatarlo."
						.formatted(eur(amount), eur(limit),
								req.employment() == Employment.SELF_EMPLOYED ? ", incluido el plan simplificado de autónomos"
										: "; si tu empresa tiene plan de empleo el límite conjunto sube hasta 10.000 €",
								eur(saving)),
					round(amount / 12)));
			remaining -= amount;
		}
		if (remaining > 0) {
			boolean conservative = req.riskProfile() == RiskProfile.CONSERVATIVE;
			double limit = conservative ? PensionRules.SIALP_LIMIT : PensionRules.PIAS_LIMIT;
			double amount = Math.min(remaining, limit);
			recs.add(conservative
					? new Recommendation("SIALP", "Seguro Individual de Ahorro a Largo Plazo (SIALP)",
							"Hasta %s €/año. Rendimientos exentos si lo mantienes 5 años y lo cobras en forma de capital."
								.formatted(eur(limit)),
							round(amount / 12))
					: new Recommendation("PIAS", "Plan Individual de Ahorro Sistemático (PIAS)",
							"Hasta %s €/año. Si al jubilarte lo transformas en renta vitalicia, la rentabilidad acumulada queda exenta."
								.formatted(eur(limit)),
							round(amount / 12)));
			remaining -= amount;
		}
		if (remaining > 0) {
			recs.add(new Recommendation("FUNDS", "Fondos de inversión de bajo coste",
					"El resto, en fondos indexados diversificados acordes a tu perfil: puedes traspasarlos entre fondos sin tributar.",
					round(remaining / 12)));
		}
		if (gap > 0) {
			recs.add(new Recommendation("ANNUITY", "Renta vitalicia al jubilarte",
					"Con el capital objetivo (%s €) una renta vitalicia pagaría unos %s €/mes de por vida, eliminando el riesgo de que el ahorro se agote si vives más de lo esperado."
						.formatted(eur(capital), eur(annuity)),
					null));
		}
		if (monthly == 0 && stressedMonthly > 0) {
			recs.add(new Recommendation("BUFFER", "Colchón de prudencia",
					"Con la legislación actual tu pensión cubre tu objetivo, pero si las pensiones fueran un 20 % menores necesitarías este ahorro. Un plan de pensiones o un PIAS son buenas opciones para construirlo.",
					round(stressedMonthly)));
		}
		if (req.dependentsCount() > 0) {
			recs.add(new Recommendation("LIFE_INSURANCE", "Seguro de vida riesgo",
					"Tienes %d persona(s) a cargo: un capital de referencia de unas 5 anualidades de tu salario (%s €) protege su nivel de vida y el plan de ahorro si faltas. Ajústalo a tu hipoteca y deudas."
						.formatted(req.dependentsCount(), eur(req.grossAnnualSalary() * 5)),
					null));
		}
		scenarios.stream()
			.filter(s -> s.profile() == req.riskProfile() && s.retirementAge() == retirementAge + 1)
			.findFirst()
			.ifPresent(later -> {
				double pensionGain = later.pensionMonthly() - pension.monthly();
				if (pensionGain > 0 && gap > 0) {
					recs.add(new Recommendation("DELAY", "Valora retrasar un año la jubilación",
							"Jubilándote a los %d tu pensión subiría unos %s €/mes y el ahorro necesario bajaría a %s €/mes."
								.formatted(later.retirementAge(), eur(pensionGain), eur(later.monthlySaving())),
							null));
				}
			});
		if (yearsToRetirement < 10 && req.riskProfile() == RiskProfile.DYNAMIC) {
			recs.add(new Recommendation("DERISK", "Reduce riesgo al acercarte a la jubilación",
					"Quedan menos de 10 años: conviene ir pasando el ahorro a activos de menor volatilidad.", null));
		}
		return recs;
	}

	private static void addWarnings(PlanRequest req, Pension pension, double age, int retirementAge,
			List<String> warnings) {
		if (!pension.eligible()) {
			warnings.add("Con %s años cotizados a los %d no alcanzas los 15 años mínimos: no tendrías pensión contributiva (sí podrías optar a una no contributiva)."
				.formatted(fmt(pension.contributionYears()), retirementAge));
		}
		if (req.grossAnnualSalary() / 12 > PensionRules.MAX_CONTRIBUTION_BASE) {
			warnings.add("Tu salario supera la base máxima de cotización (%s €/mes): la pensión pública se calcula sobre ese tope."
				.formatted(eur(PensionRules.MAX_CONTRIBUTION_BASE)));
		}
		if (pension.capped()) {
			warnings.add("Tu pensión estimada alcanza la pensión máxima (%s €/mes en 14 pagas)."
				.formatted(eur(PensionRules.MAX_PENSION)));
		}
		switch (req.employment()) {
			case SELF_EMPLOYED -> warnings.add(
					"Como autónomo/a tu pensión depende de la base por la que cotices, que puede ser inferior a tus ingresos; la estimación usa tus ingresos como base.");
			case CIVIL_SERVANT -> warnings.add(
					"Si ingresaste como funcionario/a antes de 2011 estás en Clases Pasivas, con otras reglas; la estimación usa el Régimen General.");
			case UNEMPLOYED, INACTIVE -> {
				if (req.contributing()) {
					warnings.add("Se asume que volverás a cotizar de forma continuada hasta la jubilación.");
				}
			}
			default -> {
			}
		}
		if (age >= retirementAge) {
			warnings.add("Ya has alcanzado la edad de jubilación elegida: el plan calcula el capital que necesitarías hoy.");
		}
		warnings.add("La base reguladora se estima como la media de tus bases de los 25 años previos a la jubilación, suponiendo un crecimiento salarial real del %s %% anual y en euros de hoy (desde 2026 la Seguridad Social permite también 29 años descontando los 2 peores)."
			.formatted(fmt(req.wageGrowth() * 100)));
		warnings.add("No se incluyen complementos a mínimos ni la tributación en IRPF de la pensión.");
	}

	private static double round(double v) {
		return Math.round(v * 100) / 100.0;
	}

	private static double round4(double v) {
		return Math.round(v * 10_000) / 10_000.0;
	}

	private static String eur(double v) {
		return String.format(Locale.forLanguageTag("es-ES"), "%,.0f", v);
	}

	private static String fmt(double v) {
		return String.format(Locale.forLanguageTag("es-ES"), "%.1f", v);
	}

}
