package com.example.tasks.retirement;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tasks.retirement.BankProducts.Role;
import com.example.tasks.retirement.BankProducts.Suggestion;
import com.example.tasks.retirement.PlanRequest.Employment;
import com.example.tasks.retirement.PlanRequest.RiskProfile;
import com.example.tasks.retirement.PlanRequest.Sex;
import com.example.tasks.retirement.products.ProductCatalog;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ProductMatcherTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

	private final RetirementPlanner planner = new RetirementPlanner();

	private final ProductMatcher matcher;

	ProductMatcherTests() throws Exception {
		this.matcher = new ProductMatcher(new ProductCatalog());
	}

	private static MarketAssumptions market() {
		Map<Integer, Double> le = new TreeMap<>();
		for (int age = 40; age <= 84; age++) {
			le.put(age, 86.0 - age);
		}
		return new MarketAssumptions(0.0353, 0.04, TODAY, le, 2024);
	}

	private BankProducts match(LocalDate birth, double salary, double years, double savings, RiskProfile profile,
			int dependents) {
		PlanRequest req = new PlanRequest(birth, Sex.FEMALE, Employment.EMPLOYEE, salary, years, null, null, savings,
				80.0, profile, dependents, 0.5);
		RetirementPlan plan = planner.plan(req, market(), TODAY);
		return matcher.match(req, plan, TODAY);
	}

	private static Suggestion recommended(BankProducts p, Role role) {
		return p.suggestions().stream().filter(s -> s.role() == role && s.recommended()).findFirst().orElseThrow();
	}

	@Test
	void catalogLoadsAndFlagsUnverifiedData() throws Exception {
		ProductCatalog.Catalog cat = new ProductCatalog().catalog();
		assertThat(cat.verifiedOn()).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(cat.product("cabk-jubilacion").totalFee()).isEqualTo(0.60, org.assertj.core.api.Assertions.within(1e-9));
		assertThat(cat.product("cabk-equilibrio").totalFee()).isNull();
		assertThat(cat.unavailable()).extracting(ProductCatalog.Unavailable::name).anyMatch(n -> n.contains("SIALP"));
	}

	@Test
	void youngDynamicHighEarnerGetsLifecyclePlanMatchingRetirementYear() {
		// born 1991 -> 35 years old, ordinary retirement at 65 in ~2056
		BankProducts p = match(LocalDate.of(1991, 6, 1), 80_000, 10, 0, RiskProfile.DYNAMIC, 0);

		Suggestion best = p.suggestions().stream().filter(Suggestion::best).findFirst().orElseThrow();
		assertThat(best.role()).isEqualTo(Role.PENSION_PLAN);
		assertThat(best.name()).isEqualTo("CABK Destino 2060");
		assertThat(best.totalFee()).isEqualTo(1.67, org.assertj.core.api.Assertions.within(1e-9));
		assertThat(best.feeCostAtRetirement()).isPositive();
		assertThat(p.summary()).startsWith("La opción que mejor encaja contigo es CABK Destino 2060");
		// exactly one best product in the whole plan
		assertThat(p.suggestions()).filteredOn(Suggestion::best).hasSize(1);
		// an annuity is offered for the payout phase
		assertThat(recommended(p, Role.PAYOUT).productId()).isEqualTo("renta-vitalicia");
	}

	@Test
	void conservativeNearRetirementGetsLowFeeConservativePlanAndGuaranteedProducts() {
		// born 1966 -> 60 years old, 5 years to go, 60.000 € saved
		BankProducts p = match(LocalDate.of(1966, 1, 1), 90_000, 30, 60_000, RiskProfile.CONSERVATIVE, 0);

		assertThat(recommended(p, Role.PENSION_PLAN).productId()).isEqualTo("cabk-jubilacion");
		assertThat(recommended(p, Role.SAVINGS).productId()).isEqualTo("caixafuturo");
		assertThat(recommended(p, Role.LUMP_SUM).productId()).isEqualTo("cabk-ahorro-garantizado");
		assertThat(recommended(p, Role.SAVINGS).cautions()).anyMatch(c -> c.contains("inflación"));
	}

	@Test
	void dependentsTiltSavingsTowardsPackWithLifeCover() {
		BankProducts p = match(LocalDate.of(1988, 1, 1), 85_000, 12, 0, RiskProfile.BALANCED, 2);

		Suggestion savings = recommended(p, Role.SAVINGS);
		assertThat(savings.productId()).isEqualTo("mybox-jubilacion");
		assertThat(savings.reasons().getFirst()).contains("seguro de vida");
	}

	@Test
	void runnerUpExplainsWhyItIsNotFirst() {
		BankProducts p = match(LocalDate.of(1991, 6, 1), 80_000, 10, 0, RiskProfile.DYNAMIC, 0);

		Suggestion second = p.suggestions()
			.stream()
			.filter(s -> s.role() == Role.PENSION_PLAN && s.rank() == 2)
			.findFirst()
			.orElseThrow();
		BankProducts.Comparison why = second.versusBest();
		assertThat(why.winnerName()).isEqualTo("CABK Destino 2060");
		assertThat(why.winnerScore()).isGreaterThan(second.score());
		// every criterion is accounted for: the score gap equals the sum of the per-criterion deltas
		int gap = why.winnerScore() - second.score();
		int deltas = second.breakdown().stream().mapToInt(BankProducts.ScoreItem::points).sum()
				- p.suggestions().stream().filter(s -> s.rank() == 1 && s.role() == Role.PENSION_PLAN).findFirst()
					.orElseThrow().breakdown().stream().mapToInt(BankProducts.ScoreItem::points).sum();
		assertThat(-deltas).isEqualTo(gap);
		assertThat(why.losses()).anyMatch(l -> l.startsWith("Gestión por ciclo de vida"));
		assertThat(why.verdict()).contains(second.name()).contains("CABK Destino 2060");
		// an unpublished fee is never listed as an advantage
		assertThat(why.gains()).noneMatch(g -> g.contains("no publicada"));
		// the winner itself has no comparison
		assertThat(p.suggestions()).filteredOn(s -> s.rank() == 1).allMatch(s -> s.versusBest() == null);
	}

	@Test
	void feeCostGrowsWithFeesAndHorizon() {
		BankProducts p = match(LocalDate.of(1991, 6, 1), 80_000, 10, 0, RiskProfile.BALANCED, 0);
		Suggestion destino = p.suggestions().stream().filter(s -> s.productId().equals("cabk-destino")).findFirst().orElseThrow();
		Suggestion jubilacion = p.suggestions().stream().filter(s -> s.productId().equals("cabk-jubilacion")).findFirst().orElseThrow();
		assertThat(destino.feeCostAtRetirement()).isGreaterThan(jubilacion.feeCostAtRetirement());
	}

}
