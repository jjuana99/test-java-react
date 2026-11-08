package com.example.tasks.retirement;

import com.example.tasks.retirement.BankProducts.Role;
import com.example.tasks.retirement.BankProducts.Suggestion;
import com.example.tasks.retirement.PlanRequest.Employment;
import com.example.tasks.retirement.ProductPlan.Holding;
import com.example.tasks.retirement.products.ProductCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Funds the plan with the recommended products of each role (pension plan up to its tax
 * limit, the rest in the complementary savings product) and simulates them month by month
 * with their expected return and fees.
 */
@Component
public class ProductPlanBuilder {

	/** Fee assumed when a product does not publish it (%/year). */
	static final double ASSUMED_FEE = 1.5;

	private final ProductCatalog catalog;

	public ProductPlanBuilder(ProductCatalog catalog) {
		this.catalog = catalog;
	}

	/** How a product's balance evolves: gross real return and yearly fee as fractions. */
	record Engine(String productId, String name, double grossReal, double fee, boolean feeAssumed) {
	}

	record Simulation(double contributions, double feesPaid, double finalBalance) {
	}

	/** Real return expected for a 1-7 risk level before fees: safe rate plus a risk premium. */
	static double premiumForRisk(Integer risk) {
		if (risk == null || risk <= 2) {
			return 0;
		}
		return switch (risk) {
			case 3 -> 0.01;
			case 4 -> 0.025;
			default -> 0.035;
		};
	}

	public ProductPlan build(PlanRequest req, RetirementPlan plan, BankProducts products) {
		boolean base = plan.monthlySaving() > 0;
		double capitalTarget = base ? plan.capitalNeeded() : plan.stressedCapitalNeeded();
		double gap = base ? plan.gapAnnual() : plan.stressedGapAnnual();
		int months = (int) Math.round(plan.yearsToRetirement() * 12);
		Optional<Suggestion> pensionPlan = recommended(products, Role.PENSION_PLAN);
		Optional<Suggestion> savings = recommended(products, Role.SAVINGS);
		if (capitalTarget <= 0 || months <= 0 || (pensionPlan.isEmpty() && savings.isEmpty())) {
			return null;
		}
		double safe = plan.rates().retirementReal();
		List<String> assumptions = new ArrayList<>();
		Engine pp = pensionPlan.map(s -> engine(s, safe, products, assumptions)).orElse(null);
		Engine sv = savings.map(s -> engine(s, safe, products, assumptions)).orElse(null);
		double ppLimit = pp == null ? 0
				: (PensionRules.PENSION_PLAN_LIMIT
						+ (req.employment() == Employment.SELF_EMPLOYED ? PensionRules.SELF_EMPLOYED_PLAN_EXTRA : 0)) / 12;
		// current savings keep growing as in the plan; the products fund the rest
		double toFund = Math.max(0, capitalTarget - plan.savingsAtRetirement());

		double monthly = solve(toFund, pp, sv, ppLimit, months, true);
		double withoutFees = solve(toFund, pp, sv, ppLimit, months, false);
		double ppMonthly = sv == null ? monthly : Math.min(monthly, ppLimit);
		double svMonthly = monthly - ppMonthly;

		List<Holding> holdings = new ArrayList<>();
		double contributions = req.savings();
		double fees = 0;
		double capital = plan.savingsAtRetirement();
		for (Object[] part : new Object[][] { { pp, ppMonthly }, { sv, svMonthly } }) {
			Engine e = (Engine) part[0];
			double m = (double) part[1];
			if (e == null || m <= 0) {
				continue;
			}
			Simulation sim = simulate(e, m, months, true);
			holdings.add(new Holding(e.productId(), e.name(), round(m), round4(e.grossReal()), round(e.fee() * 100),
					e.feeAssumed(), round4(netRate(e)), round(sim.contributions()),
					round(sim.finalBalance() - sim.contributions()), round(sim.feesPaid()), round(sim.finalBalance())));
			contributions += sim.contributions();
			fees += sim.feesPaid();
			capital += sim.finalBalance();
		}

		// average yearly retirement income: what comes from contributions and what from returns
		double lifetime = gap * plan.horizonYears();
		double fromContributions = lifetime > 0 ? gap * Math.min(1, contributions / lifetime) : 0;
		double pension = base ? plan.pension().annual() : plan.pension().annual() * (1 - RetirementPlanner.STRESS);
		ProductPlan.RetirementIncome income = new ProductPlan.RetirementIncome(round(pension + gap), round(pension),
				round(fromContributions), round(gap - fromContributions));

		double net = NetSalary.netAnnual(req.grossAnnualSalary()) / 12;
		double avgFees = fees / months;
		double taxSaving = ppMonthly * PensionRules.marginalTaxRate(req.grossAnnualSalary());
		ProductPlan.MonthlyBudget budget = new ProductPlan.MonthlyBudget(round(net), round(Math.max(0, monthly - avgFees)),
				round(Math.min(avgFees, monthly)), round(taxSaving), round(net - monthly + taxSaving));

		assumptions.add("Rentabilidad real esperada antes de comisiones según el riesgo del producto: el tipo sin riesgo del BCE (%s %%) más una prima de 1 %% (riesgo 3), 2,5 %% (riesgo 4) o 3,5 %% (riesgo 5 o más)."
			.formatted(num(safe * 100)));
		assumptions.add("Sueldo neto estimado con una cotización a la Seguridad Social del 6,5 % y la escala general del IRPF; varía según tu comunidad y situación familiar.");
		if (taxSaving > 0) {
			assumptions.add("Lo que te queda para gastar incluye el ahorro en el IRPF por las aportaciones al plan de pensiones.");
		}
		if (monthly > net) {
			assumptions.add("El ahorro necesario supera tu sueldo neto: valora jubilarte más tarde o rebajar el objetivo de ingresos.");
		}
		return new ProductPlan(base ? "BASE" : "PRUDENCE", products.provider(), round(monthly), round(withoutFees),
				holdings, round(contributions), round(capital - contributions), round(fees), round(capital), income,
				budget, assumptions);
	}

	private static Optional<Suggestion> recommended(BankProducts products, Role role) {
		return products.suggestions().stream().filter(s -> s.role() == role && s.recommended()).findFirst();
	}

	private Engine engine(Suggestion s, double safe, BankProducts products, List<String> assumptions) {
		ProductCatalog.Catalog cat = catalog.catalog();
		if ("caixafuturo".equals(s.productId())) {
			ProductCatalog.Product p = cat.product("caixafuturo");
			double real = (1 + p.guaranteedRate() / 100) / (1 + MarketAssumptions.INFLATION) - 1;
			assumptions.add("CaixaFuturo: se supone que el interés garantizado del %s %% se renueva cada año (el mínimo contractual es el %s %%)."
				.formatted(num(p.guaranteedRate()), num(p.minimumRate())));
			return new Engine(s.productId(), s.name(), real, 0, false);
		}
		Integer risk = s.risk();
		Double fee = s.totalFee();
		boolean assumed = false;
		if ("mybox-jubilacion".equals(s.productId())) {
			// MyBox invests through the CABK Destino range: use its risk and published fee
			Suggestion destino = products.suggestions()
				.stream()
				.filter(x -> "cabk-destino".equals(x.productId()))
				.findFirst()
				.orElse(null);
			risk = destino != null ? destino.risk() : Integer.valueOf(4);
			fee = destino != null ? destino.totalFee() : null;
			assumed = true;
			assumptions.add("MyBox Jubilación no publica comisiones: se suponen el riesgo y la comisión de la gama CABK Destino (%s %%)."
				.formatted(fee == null ? num(ASSUMED_FEE) : num(fee)));
		}
		if (fee == null) {
			fee = ASSUMED_FEE;
			assumed = true;
			assumptions.add("%s no publica comisiones: se supone un %s %% anual.".formatted(s.name(), num(ASSUMED_FEE)));
		}
		return new Engine(s.productId(), s.name(), safe + premiumForRisk(risk), fee / 100, assumed);
	}

	private static double netRate(Engine e) {
		return (1 + e.grossReal()) * (1 - e.fee()) - 1;
	}

	/** Month-by-month balance: grows at the gross return, pays the fee on the balance, adds the contribution. */
	static Simulation simulate(Engine e, double monthly, int months, boolean withFees) {
		double i = Math.pow(1 + e.grossReal(), 1.0 / 12) - 1;
		double monthlyFee = withFees ? e.fee() / 12 : 0;
		double balance = 0;
		double fees = 0;
		for (int m = 0; m < months; m++) {
			balance *= 1 + i;
			double fee = balance * monthlyFee;
			fees += fee;
			balance = balance - fee + monthly;
		}
		return new Simulation(monthly * months, fees, balance);
	}

	/** Total monthly contribution whose products reach {@code target}, split pension plan first. */
	static double solve(double target, Engine pp, Engine sv, double ppLimit, int months, boolean withFees) {
		if (target <= 0) {
			return 0;
		}
		double lo = 0;
		double hi = Math.max(1, target / months) * 4;
		while (balance(hi, pp, sv, ppLimit, months, withFees) < target && hi < 1e9) {
			hi *= 2;
		}
		for (int k = 0; k < 80; k++) {
			double mid = (lo + hi) / 2;
			if (balance(mid, pp, sv, ppLimit, months, withFees) < target) {
				lo = mid;
			}
			else {
				hi = mid;
			}
		}
		return hi;
	}

	private static double balance(double monthly, Engine pp, Engine sv, double ppLimit, int months,
			boolean withFees) {
		double toPp = pp == null ? 0 : sv == null ? monthly : Math.min(monthly, ppLimit);
		double toSv = monthly - toPp;
		double total = 0;
		if (pp != null && toPp > 0) {
			total += simulate(pp, toPp, months, withFees).finalBalance();
		}
		if (sv != null && toSv > 0) {
			total += simulate(sv, toSv, months, withFees).finalBalance();
		}
		return total;
	}

	private static double round(double v) {
		return Math.round(v * 100) / 100.0;
	}

	private static double round4(double v) {
		return Math.round(v * 10_000) / 10_000.0;
	}

	private static String num(double v) {
		return String.format(Locale.forLanguageTag("es-ES"), "%.2f", v).replaceAll(",?0+$", "");
	}

}
