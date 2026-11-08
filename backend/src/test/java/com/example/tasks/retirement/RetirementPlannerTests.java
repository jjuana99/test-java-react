package com.example.tasks.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.tasks.retirement.PlanRequest.Employment;
import com.example.tasks.retirement.PlanRequest.RiskProfile;
import com.example.tasks.retirement.PlanRequest.Sex;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class RetirementPlannerTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

	private final RetirementPlanner planner = new RetirementPlanner();

	private static MarketAssumptions market() {
		Map<Integer, Double> le = new TreeMap<>();
		for (int age = 40; age <= 84; age++) {
			le.put(age, 86.0 - age); // simple synthetic table
		}
		// 10y nominal 3.53 % and 2 % inflation -> 1.5 % real
		return new MarketAssumptions(0.0353, 0.04, TODAY, le, 2024);
	}

	private static PlanRequest request(double salary, double years, double savings, RiskProfile profile) {
		return new PlanRequest(LocalDate.of(1986, 10, 13), Sex.FEMALE, Employment.EMPLOYEE, salary, years, null,
				null, savings, 75.0, profile, 0, 0.0);
	}

	@Test
	void pensionScale() {
		assertThat(PensionRules.percentage(14.9)).isZero();
		assertThat(PensionRules.percentage(15)).isEqualTo(50);
		assertThat(PensionRules.percentage(36)).isCloseTo(97.84, within(1e-9));
		assertThat(PensionRules.percentage(37)).isEqualTo(100);
		assertThat(PensionRules.percentage(45)).isEqualTo(100);
		assertThat(PensionRules.ordinaryAge(38.5)).isEqualTo(65);
		assertThat(PensionRules.ordinaryAge(38.4)).isEqualTo(67);
	}

	@Test
	void buildsPlanForMidCareerEmployee() {
		// 40 years old, 18 years contributed, 36.000 € gross
		RetirementPlan plan = planner.plan(request(36_000, 18, 10_000, RiskProfile.BALANCED), market(), TODAY);

		assertThat(plan.currentAge()).isCloseTo(40, within(0.01));
		// 18 + 25 = 43 years at 65 -> ordinary age 65, full pension
		assertThat(plan.ordinaryAge()).isEqualTo(65);
		assertThat(plan.pension().percentage()).isEqualTo(100);
		// base 3.000 €/month -> regulatory base 3.000*12/14
		assertThat(plan.pension().monthly()).isCloseTo(2_571.43, within(0.01));
		// target is 75 % of 36.000 = 27.000 €/year, below the 36.000 € pension: no gap
		assertThat(plan.targetAnnualIncome()).isEqualTo(27_000);
		assertThat(plan.gapAnnual()).isZero();
		assertThat(plan.onTrack()).isTrue();
		assertThat(plan.monthlySaving()).isZero();
	}

	@Test
	void sizesSavingsWhenPensionFallsShort() {
		// high earner: pension is capped, so there is a gap to fund
		RetirementPlan plan = planner.plan(request(90_000, 10, 0, RiskProfile.BALANCED), market(), TODAY);

		assertThat(plan.pension().capped()).isTrue();
		assertThat(plan.pension().monthly()).isEqualTo(PensionRules.MAX_PENSION);
		double gap = 90_000 * 0.75 - PensionRules.MAX_PENSION * 14;
		assertThat(plan.gapAnnual()).isCloseTo(gap, within(0.01));
		// 10 + 25 = 35 years at 65 < 38.5 -> ordinary age 67; synthetic life expectancy at 67 is 19 years
		assertThat(plan.retirementAge()).isEqualTo(67);
		assertThat(plan.horizonYears()).isEqualTo(19 + RetirementPlanner.LONGEVITY_BUFFER);
		assertThat(plan.monthlySaving()).isPositive();
		// a dynamic profile needs less monthly saving than a conservative one
		double conservative = scenario(plan, RiskProfile.CONSERVATIVE, plan.retirementAge());
		double dynamic = scenario(plan, RiskProfile.DYNAMIC, plan.retirementAge());
		assertThat(dynamic).isLessThan(plan.monthlySaving()).isLessThan(conservative);
		// the projection ends near zero: the plan is fully spent over the horizon
		assertThat(plan.projection().getLast().balance()).isLessThan(plan.capitalNeeded() * 0.05);
		assertThat(plan.recommendations()).extracting(RetirementPlan.Recommendation::kind)
			.contains("PENSION_PLAN", "PIAS", "ANNUITY");
	}

	@Test
	void warnsWhenMinimumContributionIsNotReached() {
		PlanRequest req = new PlanRequest(LocalDate.of(1965, 1, 1), Sex.MALE, Employment.INACTIVE, 20_000.0, 5.0,
				false, null, 0.0, 75.0, RiskProfile.CONSERVATIVE, 2, 0.0);

		RetirementPlan plan = planner.plan(req, market(), TODAY);

		assertThat(plan.pension().eligible()).isFalse();
		assertThat(plan.pension().monthly()).isZero();
		assertThat(plan.warnings()).anyMatch(w -> w.contains("15 años mínimos"));
		assertThat(plan.recommendations()).extracting(RetirementPlan.Recommendation::kind)
			.contains("LIFE_INSURANCE", "SIALP");
	}

	@Test
	void earlyRetirementIsNotModelledAndDelayAddsBonus() {
		PlanRequest early = new PlanRequest(LocalDate.of(1986, 10, 13), Sex.FEMALE, Employment.EMPLOYEE, 40_000.0,
				10.0, true, 63, 0.0, 75.0, RiskProfile.BALANCED, 0, 0.0);
		RetirementPlan plan = planner.plan(early, market(), TODAY);
		assertThat(plan.retirementAge()).isEqualTo(plan.ordinaryAge());
		assertThat(plan.warnings()).anyMatch(w -> w.contains("anticipada"));

		PlanRequest late = new PlanRequest(LocalDate.of(1986, 10, 13), Sex.FEMALE, Employment.EMPLOYEE, 40_000.0,
				10.0, true, 69, 0.0, 75.0, RiskProfile.BALANCED, 0, 0.0);
		assertThat(planner.plan(late, market(), TODAY).pension().delayBonusPct()).isEqualTo(8);
	}

	@Test
	void wageGrowthLowersReplacementAndStressRaisesSaving() {
		PlanRequest growing = new PlanRequest(LocalDate.of(1986, 10, 13), Sex.FEMALE, Employment.EMPLOYEE, 36_000.0,
				18.0, null, null, 0.0, 80.0, RiskProfile.BALANCED, 0, 1.0);

		RetirementPlan plan = planner.plan(growing, market(), TODAY);

		// 25 years to retirement at +1 %/year real: final salary 36.000 * 1.01^25
		assertThat(plan.finalSalary()).isCloseTo(36_000 * Math.pow(1.01, 25), within(0.01));
		// the 25-year average base sits below the final salary, so the pension replaces less than 100 %
		assertThat(plan.pension().annual() / plan.finalSalary()).isBetween(0.85, 0.92);
		// a 20 % lower public pension opens a gap even when the current-law plan is on track
		assertThat(plan.stressedMonthlySaving()).isGreaterThan(plan.monthlySaving());
		assertThat(plan.recommendations()).extracting(RetirementPlan.Recommendation::kind)
			.contains("BUFFER")
			.doesNotContain("DELAY");
	}

	private static double scenario(RetirementPlan plan, RiskProfile profile, int age) {
		return plan.scenarios()
			.stream()
			.filter(s -> s.profile() == profile && s.retirementAge() == age)
			.findFirst()
			.orElseThrow()
			.monthlySaving();
	}

}
