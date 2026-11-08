package com.example.tasks.retirement;

import java.time.LocalDate;
import java.util.List;

/** Result of the retirement planner. Amounts are in today's euros (real terms). */
public record RetirementPlan(double currentAge, int ordinaryAge, int retirementAge, double yearsToRetirement,
		Pension pension, double finalSalary, double targetAnnualIncome, double gapAnnual, double lifeExpectancy,
		int horizonYears, double capitalNeeded, double savingsAtRetirement, double monthlySaving, boolean onTrack,
		double stressedMonthlySaving, double stressedGapAnnual, double stressedCapitalNeeded, Rates rates,
		double annuityMonthly, List<Scenario> scenarios, List<ProjectionPoint> projection,
		List<Recommendation> recommendations, List<String> warnings, BankProducts bankProducts,
		ProductPlan productPlan) {

	RetirementPlan withProducts(BankProducts products, ProductPlan plan) {
		return new RetirementPlan(currentAge, ordinaryAge, retirementAge, yearsToRetirement, pension, finalSalary,
				targetAnnualIncome, gapAnnual, lifeExpectancy, horizonYears, capitalNeeded, savingsAtRetirement,
				monthlySaving, onTrack, stressedMonthlySaving, stressedGapAnnual, stressedCapitalNeeded, rates,
				annuityMonthly, scenarios, projection, recommendations, warnings, products, plan);
	}

	/** Estimated public pension. {@code monthly} is paid 14 times a year. */
	public record Pension(boolean eligible, double contributionYears, double percentage, double delayBonusPct,
			double contributionBase, double regulatoryBase, double monthly, double annual, boolean capped) {
	}

	public record Rates(double accumulationReal, double retirementReal, double safeNominal, double inflation,
			double realWageGrowth, LocalDate ratesDate, int lifeTableYear) {
	}

	public record Scenario(PlanRequest.RiskProfile profile, int retirementAge, double monthlySaving,
			double pensionMonthly) {
	}

	public record ProjectionPoint(int age, int year, double balance) {
	}

	/** {@code monthlyAmount} is null when the recommendation is not a contribution. */
	public record Recommendation(String kind, String title, String detail, Double monthlyAmount) {
	}

}
