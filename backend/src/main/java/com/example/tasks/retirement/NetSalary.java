package com.example.tasks.retirement;

/**
 * Rough net salary estimate: employee social security contributions plus IRPF with the
 * general (state + standard regional) scale. Ignores regional differences, family
 * allowances and the work-income reduction, so it is only an approximation.
 */
final class NetSalary {

	/** Employee contribution: common contingencies, unemployment, training and MEI (≈2026). */
	static final double EMPLOYEE_SS_RATE = 0.065;

	/** Fixed deductible expenses on employment income. */
	static final double OTHER_EXPENSES = 2_000;

	static final double PERSONAL_ALLOWANCE = 5_550;

	private static final double[][] SCALE = { { 12_450, 0.19 }, { 20_200, 0.24 }, { 35_200, 0.30 },
			{ 60_000, 0.37 }, { 300_000, 0.45 }, { Double.MAX_VALUE, 0.47 } };

	private NetSalary() {
	}

	static double socialSecurity(double gross) {
		return Math.min(gross, PensionRules.MAX_CONTRIBUTION_BASE * 12) * EMPLOYEE_SS_RATE;
	}

	static double incomeTax(double gross) {
		double base = Math.max(0, gross - socialSecurity(gross) - OTHER_EXPENSES);
		return Math.max(0, scale(base) - scale(Math.min(base, PERSONAL_ALLOWANCE)));
	}

	static double netAnnual(double gross) {
		return gross - socialSecurity(gross) - incomeTax(gross);
	}

	private static double scale(double base) {
		double tax = 0;
		double lower = 0;
		for (double[] bracket : SCALE) {
			double upper = bracket[0];
			if (base <= lower) {
				break;
			}
			tax += (Math.min(base, upper) - lower) * bracket[1];
			lower = upper;
		}
		return tax;
	}

}
