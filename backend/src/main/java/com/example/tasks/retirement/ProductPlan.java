package com.example.tasks.retirement;

import java.util.List;

/**
 * The retirement plan funded with the recommended bank products: their expected return and
 * fees are applied, so it shows how much really has to be saved, how much goes to the
 * provider in fees and where the retirement income comes from. Amounts in today's euros.
 *
 * @param scenario BASE when the plan funds the gap under current law, PRUDENCE when it funds
 * the 20 % lower pension scenario because the current-law plan needs no savings
 */
public record ProductPlan(String scenario, String provider, double monthlyContribution, double monthlyWithoutFees,
		List<Holding> holdings, double contributions, double returns, double feesPaid, double capitalAtRetirement,
		RetirementIncome income, MonthlyBudget budget, List<String> assumptions) {

	/**
	 * One product of the plan. Returns are real (after inflation); {@code feeAssumed} marks a
	 * fee that is not published and was assumed.
	 */
	public record Holding(String productId, String name, double monthly, double grossRealReturn, Double feePct,
			boolean feeAssumed, double netRealReturn, double contributions, double returns, double feesPaid,
			double finalBalance) {
	}

	/** Average yearly retirement income split by where it comes from. */
	public record RetirementIncome(double target, double publicPension, double fromContributions,
			double fromReturns) {
	}

	/**
	 * What happens to the monthly net salary today. {@code invested + fees} is the monthly
	 * contribution: fees are not charged separately but taken from the savings.
	 */
	public record MonthlyBudget(double netMonthlyIncome, double invested, double fees, double taxSaving,
			double leftToSpend) {
	}

}
