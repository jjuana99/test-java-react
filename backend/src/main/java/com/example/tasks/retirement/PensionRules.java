package com.example.tasks.retirement;

/**
 * Spanish contributory retirement pension rules used by the planner (Régimen General,
 * values in force in 2026, scale applicable from 2027 under RDL 8/2015 DT 9ª and Ley
 * 27/2011). It is a simplified model: no early retirement, no minimum-pension supplements,
 * and the regulatory base is approximated by the current contribution base.
 */
final class PensionRules {

	/** Maximum monthly contribution base in 2026 (€/month, 12 payments). */
	static final double MAX_CONTRIBUTION_BASE = 5_101.20;

	/** Maximum contributory pension in 2026 (€/month, 14 payments). */
	static final double MAX_PENSION = 3_359.60;

	static final int PENSION_PAYMENTS = 14;

	/** Years of contribution bases averaged into the regulatory base. */
	static final int REGULATORY_YEARS = 25;

	/** Minimum contribution years for a contributory pension (50 % of the regulatory base). */
	static final double MIN_YEARS = 15;

	/** Contribution years needed to retire at 65 from 2027 on; otherwise the ordinary age is 67. */
	static final double YEARS_FOR_EARLY_ORDINARY_AGE = 38.5;

	/** Contribution years that give 100 % of the regulatory base from 2027 on. */
	static final double YEARS_FOR_FULL_PENSION = 37;

	/** Delayed retirement supplement per full year beyond the ordinary age. */
	static final double DELAY_BONUS_PER_YEAR = 0.04;

	/** Yearly deductible limits for retirement savings products (IRPF 2026). */
	static final double PENSION_PLAN_LIMIT = 1_500;

	static final double SELF_EMPLOYED_PLAN_EXTRA = 4_250;

	static final double PIAS_LIMIT = 8_000;

	static final double SIALP_LIMIT = 5_000;

	private PensionRules() {
	}

	/** Ordinary retirement age (2027+ rule) given the contribution years the person would have at 65. */
	static int ordinaryAge(double contributionYearsAt65) {
		return contributionYearsAt65 >= YEARS_FOR_EARLY_ORDINARY_AGE ? 65 : 67;
	}

	/**
	 * Percentage of the regulatory base for {@code years} of contributions: 50 % at 15 years,
	 * +0.19 % per extra month for months 1-248 and +0.18 % for the next 16, capped at 100 %.
	 */
	static double percentage(double years) {
		if (years < MIN_YEARS) {
			return 0;
		}
		double months = Math.floor((years - MIN_YEARS) * 12 + 1e-9);
		double pct = 50 + Math.min(months, 248) * 0.19 + Math.min(Math.max(months - 248, 0), 16) * 0.18;
		return Math.min(100, pct);
	}

	/**
	 * Approximate combined (state + standard regional) IRPF marginal rate for a gross salary,
	 * after a rough 6.5 % employee social security deduction. Varies by region.
	 */
	static double marginalTaxRate(double grossAnnual) {
		double taxable = grossAnnual * (1 - 0.065);
		if (taxable <= 12_450) {
			return 0.19;
		}
		if (taxable <= 20_200) {
			return 0.24;
		}
		if (taxable <= 35_200) {
			return 0.30;
		}
		if (taxable <= 60_000) {
			return 0.37;
		}
		if (taxable <= 300_000) {
			return 0.45;
		}
		return 0.47;
	}

}
