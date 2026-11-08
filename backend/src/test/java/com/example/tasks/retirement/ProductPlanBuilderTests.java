package com.example.tasks.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.tasks.retirement.PlanRequest.Employment;
import com.example.tasks.retirement.PlanRequest.RiskProfile;
import com.example.tasks.retirement.PlanRequest.Sex;
import com.example.tasks.retirement.products.ProductCatalog;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ProductPlanBuilderTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

	private final RetirementPlanner planner = new RetirementPlanner();

	private final ProductMatcher matcher;

	private final ProductPlanBuilder builder;

	ProductPlanBuilderTests() throws Exception {
		ProductCatalog catalog = new ProductCatalog();
		this.matcher = new ProductMatcher(catalog);
		this.builder = new ProductPlanBuilder(catalog);
	}

	private static MarketAssumptions market() {
		Map<Integer, Double> le = new TreeMap<>();
		for (int age = 40; age <= 84; age++) {
			le.put(age, 86.0 - age);
		}
		return new MarketAssumptions(0.0353, 0.04, TODAY, le, 2024);
	}

	private ProductPlan build(double salary, double target, RiskProfile profile) {
		PlanRequest req = new PlanRequest(LocalDate.of(1984, 3, 15), Sex.FEMALE, Employment.EMPLOYEE, salary, 19.7,
				null, null, 15_000.0, target, profile, 0, 0.5);
		RetirementPlan plan = planner.plan(req, market(), TODAY);
		return builder.build(req, plan, matcher.match(req, plan, TODAY));
	}

	@Test
	void simulationWithoutFeesMatchesClosedForm() {
		var engine = new ProductPlanBuilder.Engine("x", "x", 0.03, 0.0167, false);
		int months = 240;
		double i = Math.pow(1.03, 1.0 / 12) - 1;
		double closedForm = 100 * (Math.pow(1 + i, months) - 1) / i;

		assertThat(ProductPlanBuilder.simulate(engine, 100, months, false).finalBalance()).isCloseTo(closedForm,
				within(0.01));
		var withFees = ProductPlanBuilder.simulate(engine, 100, months, true);
		assertThat(withFees.finalBalance()).isLessThan(closedForm);
		assertThat(withFees.feesPaid()).isPositive();
	}

	@Test
	void productsReachTheCapitalAndFeesRaiseTheSaving() {
		ProductPlan p = build(58_000, 120, RiskProfile.DYNAMIC);

		assertThat(p.scenario()).isEqualTo("BASE");
		assertThat(p.holdings()).extracting(ProductPlan.Holding::name).contains("CABK Destino 2050");
		// pension plan gets its 1.500 €/year, the rest goes to the complementary product
		assertThat(p.holdings().getFirst().monthly()).isEqualTo(125);
		assertThat(p.monthlyContribution()).isGreaterThan(p.monthlyWithoutFees());
		assertThat(p.feesPaid()).isPositive();
		assertThat(p.capitalAtRetirement()).isCloseTo(p.contributions() + p.returns(), within(0.05));
	}

	@Test
	void barsAddUp() {
		ProductPlan p = build(58_000, 120, RiskProfile.DYNAMIC);

		var income = p.income();
		assertThat(income.publicPension() + income.fromContributions() + income.fromReturns())
			.isCloseTo(income.target(), within(0.05));
		assertThat(income.fromReturns()).isPositive();

		var budget = p.budget();
		assertThat(budget.invested() + budget.fees() + budget.leftToSpend())
			.isCloseTo(budget.netMonthlyIncome() + budget.taxSaving(), within(0.05));
		assertThat(budget.invested() + budget.fees()).isCloseTo(p.monthlyContribution(), within(0.05));
	}

	@Test
	void netSalaryIsBelowGrossAndProgressive() {
		double low = NetSalary.netAnnual(20_000) / 20_000;
		double high = NetSalary.netAnnual(90_000) / 90_000;
		assertThat(low).isBetween(0.75, 0.95);
		assertThat(high).isLessThan(low);
	}

	@Test
	void nothingToFundWhenPensionCoversEvenThePrudenceScenario() {
		// modest target fully covered by the public pension even 20 % lower
		ProductPlan p = build(30_000, 50, RiskProfile.BALANCED);
		assertThat(p).isNull();
	}

}
