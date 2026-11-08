package com.example.tasks.retirement;

import com.example.tasks.retirement.BankProducts.Comparison;
import com.example.tasks.retirement.BankProducts.Role;
import com.example.tasks.retirement.BankProducts.ScoreItem;
import com.example.tasks.retirement.BankProducts.Suggestion;
import com.example.tasks.retirement.PlanRequest.Employment;
import com.example.tasks.retirement.PlanRequest.RiskProfile;
import com.example.tasks.retirement.products.ProductCatalog;
import com.example.tasks.retirement.products.ProductCatalog.Product;
import com.example.tasks.retirement.products.ProductCatalog.Variant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Scores the bank's retirement products against a person's plan. Every product of a role is
 * scored on the same criteria (risk fit, fees, lifecycle management, target date, life cover,
 * liquidity...), which makes the ranking explainable: each lower-ranked product carries a
 * criterion-by-criterion comparison with the winner. Rule based; not financial advice.
 */
@Component
public class ProductMatcher {

	private final ProductCatalog catalog;

	public ProductMatcher(ProductCatalog catalog) {
		this.catalog = catalog;
	}

	private record Context(PlanRequest req, RetirementPlan plan, double years, int retirementYear, double desiredRisk,
			double pensionPlanMonthly, double savingsMonthly) {
	}

	public BankProducts match(PlanRequest req, RetirementPlan plan, LocalDate today) {
		ProductCatalog.Catalog cat = catalog.catalog();
		double years = plan.yearsToRetirement();
		// when the current-law plan needs nothing, size products for the prudence scenario
		double monthly = plan.monthlySaving() > 0 ? plan.monthlySaving() : plan.stressedMonthlySaving();
		boolean taxable = req.grossAnnualSalary() > 0 && (req.employment() == Employment.EMPLOYEE
				|| req.employment() == Employment.SELF_EMPLOYED || req.employment() == Employment.CIVIL_SERVANT);
		double planLimit = (PensionRules.PENSION_PLAN_LIMIT
				+ (req.employment() == Employment.SELF_EMPLOYED ? PensionRules.SELF_EMPLOYED_PLAN_EXTRA : 0)) / 12;
		double pensionPlanMonthly = taxable ? Math.min(monthly, planLimit) : 0;
		Context ctx = new Context(req, plan, years, today.getYear() + (int) Math.round(years),
				desiredRisk(req.riskProfile(), years), pensionPlanMonthly, monthly - pensionPlanMonthly);

		List<Suggestion> all = new ArrayList<>();
		if (pensionPlanMonthly > 0) {
			all.addAll(rank(pensionPlans(ctx, cat)));
		}
		if (ctx.savingsMonthly() > 0) {
			all.addAll(rank(savings(ctx, cat)));
		}
		if (req.savings() >= 25_000) {
			all.addAll(rank(List.of(guaranteedLumpSum(ctx, cat.product("cabk-ahorro-garantizado")))));
		}
		if (Math.max(plan.capitalNeeded(), plan.savingsAtRetirement()) >= 20_000) {
			all.addAll(rank(List.of(annuity(ctx, cat.product("renta-vitalicia")))));
		}

		Role bestRole = pensionPlanMonthly > 0 ? Role.PENSION_PLAN : ctx.savingsMonthly() > 0 ? Role.SAVINGS : null;
		Optional<Suggestion> best = all.stream()
			.filter(s -> s.recommended() && (bestRole == null || s.role() == bestRole))
			.findFirst();
		List<Suggestion> flagged = all.stream()
			.map(s -> best.isPresent() && s == best.get() ? s.asBest() : s)
			.toList();
		String summary = best.map(b -> "La opción que mejor encaja contigo es %s: %s"
			.formatted(b.name(), lowerFirst(b.reasons().getFirst())))
			.orElse("Con la legislación actual tu pensión cubre tu objetivo; estos productos te ayudarían a gestionar el ahorro que ya tienes.");
		return new BankProducts(cat.provider(), cat.verifiedOn(), cat.note(), summary, flagged, cat.unavailable());
	}

	/** Risk level (1-7 scale) that suits a profile and horizon. */
	static double desiredRisk(RiskProfile profile, double years) {
		double risk = switch (profile) {
			case CONSERVATIVE -> 2.5;
			case BALANCED -> 3.5;
			case DYNAMIC -> 4.5;
		};
		if (years < 10) {
			risk -= 0.5;
		}
		if (years < 5) {
			risk -= 1;
		}
		return Math.max(2, risk);
	}

	// ---------- pension plans ----------

	private List<Suggestion> pensionPlans(Context ctx, ProductCatalog.Catalog cat) {
		List<Suggestion> out = new ArrayList<>();
		double taxSaving = ctx.pensionPlanMonthly() * 12 * PensionRules.marginalTaxRate(ctx.req().grossAnnualSalary());
		String taxReason = "Desgrava en el IRPF: unos %s €/año de ahorro fiscal con %s €/mes.".formatted(eur(taxSaving),
				eur(ctx.pensionPlanMonthly()));

		// lifecycle range: pick the target date closest to the retirement year
		Product destino = cat.product("cabk-destino");
		Variant v = destino.variants()
			.stream()
			.min(Comparator.comparingInt(x -> Math.abs(x.targetYear() - ctx.retirementYear())))
			.orElseThrow();
		Double rangeFee = destino.variants()
			.stream()
			.filter(x -> x.managementFee() != null && x.depositFee() != null)
			.map(x -> x.managementFee() + x.depositFee())
			.findFirst()
			.orElse(null);
		boolean variantFeeVerified = v.managementFee() != null && v.depositFee() != null;
		Double destinoFee = variantFeeVerified ? v.managementFee() + v.depositFee() : rangeFee;
		int yearGap = Math.abs(v.targetYear() - ctx.retirementYear());

		List<String> reasons = new ArrayList<>();
		reasons.add("Reduce el riesgo automáticamente hasta %d, %s tu jubilación (%d)."
			.formatted(v.targetYear(), yearGap <= 2 ? "justo cuando llega" : "cerca de", ctx.retirementYear()));
		reasons.add("Riesgo %d/7, adecuado para un perfil %s con %d años por delante.".formatted(v.risk(),
				profileName(ctx.req().riskProfile()), Math.round(ctx.years())));
		reasons.add(taxReason);
		List<String> cautions = new ArrayList<>();
		addFeeCaution(cautions, destinoFee, ctx.pensionPlanMonthly(), ctx);
		if (!variantFeeVerified && rangeFee != null) {
			cautions.add("Comisión de la gama (verificada en Destino 2030, 2040 y 2060); confirma la de %d en su ficha."
				.formatted(v.targetYear()));
		}
		if (ctx.years() < 8) {
			cautions.add("Con poco horizonte la gestión por ciclo de vida aporta menos.");
		}
		cautions.add("Rentabilidad a 12 meses del %s %%: las rentabilidades pasadas no garantizan las futuras."
			.formatted(num(v.return12m())));
		List<ScoreItem> breakdown = List.of(riskItem(v.risk(), ctx),
				new ScoreItem("lifecycle", "Gestión por ciclo de vida", ctx.years() >= 8 ? 15 : 0, 15,
						"reduce el riesgo automáticamente hasta " + v.targetYear()),
				new ScoreItem("target", "Fecha objetivo", yearGap <= 2 ? 10 : yearGap <= 5 ? 5 : 0, 10,
						"fecha %d para jubilarte en %d".formatted(v.targetYear(), ctx.retirementYear())),
				feeItem(destinoFee, ctx.pensionPlanMonthly(), ctx),
				new ScoreItem("nearRetirement", "Pensado para los últimos años", 0, 10, "no es su objetivo principal"));
		out.add(suggestion(destino, "CABK Destino " + v.targetYear(), Role.PENSION_PLAN, ctx.pensionPlanMonthly(),
				v.risk(), destinoFee, feeCost(destinoFee, ctx.pensionPlanMonthly(), ctx), v.return12m(), reasons,
				cautions, breakdown));

		for (String id : List.of("cabk-jubilacion", "cabk-equilibrio", "cabk-crecimiento", "cabk-monetario")) {
			Product p = cat.product(id);
			Double fee = p.totalFee();
			List<String> r = new ArrayList<>();
			if (fee != null && fee < 1) {
				r.add("Tiene una comisión baja (%s %% al año en total).".formatted(num(fee)));
			}
			r.add("Riesgo %d/7: %s.".formatted(p.risk(), lowerFirst(p.description())));
			r.add(taxReason);
			List<String> c = new ArrayList<>();
			addFeeCaution(c, fee, ctx.pensionPlanMonthly(), ctx);
			if (p.risk() <= 3 && ctx.years() >= 15 && ctx.req().riskProfile() != RiskProfile.CONSERVATIVE) {
				c.add("Con %d años de horizonte, su poca exposición a bolsa puede limitar el crecimiento."
					.formatted(Math.round(ctx.years())));
			}
			boolean nearRetirement = "CONSERVATIVE_MIXED".equals(p.category());
			List<ScoreItem> items = List.of(riskItem(p.risk(), ctx),
					new ScoreItem("lifecycle", "Gestión por ciclo de vida", 0, 15,
							"mantiene el mismo riesgo hasta tu jubilación"),
					new ScoreItem("target", "Fecha objetivo", 0, 10, "no tiene fecha objetivo"),
					feeItem(fee, ctx.pensionPlanMonthly(), ctx),
					new ScoreItem("nearRetirement", "Pensado para los últimos años",
							nearRetirement && ctx.years() < 10 ? 10 : 0, 10,
							nearRetirement ? "pensado para cuando quedan pocos años" : "no es su objetivo principal"));
			out.add(suggestion(p, p.name(), Role.PENSION_PLAN, ctx.pensionPlanMonthly(), p.risk(), fee,
					feeCost(fee, ctx.pensionPlanMonthly(), ctx), p.return12m(), r, c, items));
		}
		return out;
	}

	private static ScoreItem riskItem(Integer risk, Context ctx) {
		int points = risk == null ? 15 : (int) Math.max(0, 40 - 12 * Math.abs(risk - ctx.desiredRisk()));
		String detail = risk == null ? "riesgo no publicado"
				: "riesgo %d/7 cuando a tu perfil le encaja ≈%s/7".formatted(risk, num(ctx.desiredRisk()));
		return new ScoreItem("risk", "Riesgo adecuado a tu perfil y horizonte", points, 40, detail, risk != null);
	}

	private static ScoreItem feeItem(Double fee, double monthly, Context ctx) {
		if (fee == null) {
			return new ScoreItem("fees", "Comisiones", 8, 25, "comisión no publicada", false);
		}
		Double cost = feeCost(fee, monthly, ctx);
		return new ScoreItem("fees", "Comisiones", (int) Math.round(25 * (1 - Math.min(fee, 2) / 2)), 25,
				"%s %% al año (≈%s € de coste al jubilarte)".formatted(num(fee), eur(cost == null ? 0 : cost)));
	}

	// ---------- complementary savings ----------

	private List<Suggestion> savings(Context ctx, ProductCatalog.Catalog cat) {
		RiskProfile profile = ctx.req().riskProfile();
		int dependents = ctx.req().dependentsCount();
		boolean longHorizon = ctx.years() >= 10;

		Product mybox = cat.product("mybox-jubilacion");
		List<String> r = new ArrayList<>();
		if (dependents > 0) {
			r.add("Incluye un seguro de vida y de invalidez que protege a tus %d persona(s) a cargo.".formatted(dependents));
		}
		r.add("Combina plan de pensiones e inversión rescatable en un solo producto, gestionados por fecha objetivo.");
		if (longHorizon) {
			r.add("Con %d años por delante, la parte invertida tiene tiempo para crecer.".formatted(Math.round(ctx.years())));
		}
		List<String> c = List.of("La parte Unit Linked no garantiza rentabilidad: puede tener pérdidas.",
				"La web no publica sus comisiones: pide el documento de datos fundamentales antes de contratar.");
		List<ScoreItem> myboxItems = List.of(
				new ScoreItem("profile", "Encaje con tu perfil de riesgo", profile == RiskProfile.CONSERVATIVE ? 5 : 30, 35,
						"invierte sin garantía, pensado para perfiles moderados o dinámicos"),
				new ScoreItem("protection", "Protección de tu familia", dependents > 0 ? 25 : 0, 25,
						"incluye seguro de vida e invalidez"),
				new ScoreItem("horizon", "Horizonte temporal", longHorizon ? 15 : 0, 15,
						"pensado para 10 años o más"),
				new ScoreItem("liquidity", "Liquidez", 8, 15, "la parte invertida se rescata a valor de mercado"),
				new ScoreItem("flexibility", "Aportación mínima y garantía", 0, 10, "sin garantía de capital"));
		Suggestion myboxSuggestion = suggestion(mybox, mybox.name(), Role.SAVINGS, ctx.savingsMonthly(), null, null,
				null, null, r, c, myboxItems);

		Product futuro = cat.product("caixafuturo");
		List<String> r2 = List.of("Garantiza el capital (riesgo 1/6) y puedes rescatarlo cuando quieras sin penalización.",
				"Empiezas con %s €/mes y, cobrado como renta vitalicia tras 5 años, la rentabilidad queda exenta."
					.formatted(eur(futuro.minMonthly())));
		List<String> c2 = new ArrayList<>();
		c2.add("Interés garantizado del %s %% durante 12 meses (mínimo %s %%): por debajo de una inflación del 2 %%, pierde poder adquisitivo."
			.formatted(num(futuro.guaranteedRate()), num(futuro.minimumRate())));
		if (ctx.savingsMonthly() * 12 > futuro.maxAnnual()) {
			c2.add("Admite como máximo %s €/año; el resto tendría que ir a otro producto.".formatted(eur(futuro.maxAnnual())));
		}
		List<ScoreItem> futuroItems = List.of(
				new ScoreItem("profile", "Encaje con tu perfil de riesgo",
						profile == RiskProfile.CONSERVATIVE ? 35 : profile == RiskProfile.BALANCED ? 15 : 5, 35,
						"capital garantizado al %s %%, pensado para perfiles conservadores".formatted(num(futuro.guaranteedRate()))),
				new ScoreItem("protection", "Protección de tu familia", 0, 25,
						"solo un 1 % extra (máx. 600 €) si falleces, sin seguro de vida"),
				new ScoreItem("horizon", "Horizonte temporal", longHorizon ? 0 : 10, 15,
						"útil cuando quedan menos de 10 años"),
				new ScoreItem("liquidity", "Liquidez", 15, 15, "rescate libre en cualquier momento, sin penalización"),
				new ScoreItem("flexibility", "Aportación mínima y garantía", 10, 10,
						"desde %s €/mes y capital garantizado".formatted(eur(futuro.minMonthly()))));
		Suggestion futuroSuggestion = suggestion(futuro, futuro.name(), Role.SAVINGS, ctx.savingsMonthly(),
				futuro.risk(), null, null, null, r2, c2, futuroItems);
		return List.of(myboxSuggestion, futuroSuggestion);
	}

	// ---------- single-product roles ----------

	private Suggestion guaranteedLumpSum(Context ctx, Product p) {
		boolean fits = ctx.req().riskProfile() == RiskProfile.CONSERVATIVE || ctx.years() <= 5;
		List<String> r = List.of(
				"Protege los %s € que ya tienes con un %s %% garantizado el primer año (1,5 %% desde 50.000 €)."
					.formatted(eur(ctx.req().savings()), num(p.guaranteedRate())),
				"Riesgo 1/6, adecuado si te quedan pocos años o prefieres no asumir riesgo.");
		List<String> c = List.of("Si rescatas antes de 3 años pierdes el 50 % de las ganancias y no admite rescates parciales.",
				"Tras el primer año el mínimo garantizado es del 0,05 %, por debajo de la inflación.");
		List<ScoreItem> items = List.of(new ScoreItem("fit", "Encaje con tu perfil y horizonte", fits ? 65 : 35, 100,
				fits ? "perfil conservador o pocos años hasta la jubilación" : "tu perfil admite más riesgo"));
		return suggestion(p, p.name(), Role.LUMP_SUM, null, p.risk(), null, null, null, r, c, items);
	}

	private Suggestion annuity(Context ctx, Product p) {
		List<String> r = new ArrayList<>();
		r.add("Elimina el riesgo de que el ahorro se agote si vives más de lo esperado.");
		if (ctx.plan().annuityMonthly() > 0) {
			r.add("Con el capital objetivo pagaría unos %s €/mes de por vida (estimación con los tipos del BCE)."
				.formatted(eur(ctx.plan().annuityMonthly())));
		}
		r.add("Fiscalmente solo tributa una parte de la renta, menor cuanto más tarde empieces a cobrarla.");
		List<String> c = List.of("Prima única mínima de %s €.".formatted(eur(p.minPremium())),
				"Según la modalidad, el capital no se recupera: compara opciones con o sin reversión a herederos.");
		List<ScoreItem> items = List.of(new ScoreItem("longevity", "Cobertura del riesgo de longevidad", 70, 100,
				"renta garantizada de por vida"));
		return suggestion(p, p.name(), Role.PAYOUT, null, null, null, null, null, r, c, items);
	}

	// ---------- ranking and explanations ----------

	private static Suggestion suggestion(Product p, String name, Role role, Double monthly, Integer risk, Double fee,
			Double feeCost, Double return12m, List<String> reasons, List<String> cautions, List<ScoreItem> breakdown) {
		int score = Math.max(0, Math.min(100, breakdown.stream().mapToInt(ScoreItem::points).sum()));
		return new Suggestion(p.id(), name, p.type(), role, role.getLabel(), score, 0, false, false,
				monthly == null ? null : round(monthly), risk, p.riskScale(), fee, feeCost, return12m, reasons,
				cautions, breakdown, null, p.description(), p.url());
	}

	/** Sorts by score, numbers the ranks and explains every product against the winner. */
	private static List<Suggestion> rank(List<Suggestion> group) {
		List<Suggestion> sorted = group.stream().sorted(Comparator.comparingInt(Suggestion::score).reversed()).toList();
		Suggestion winner = sorted.getFirst();
		List<Suggestion> out = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Suggestion s = sorted.get(i);
			out.add(s.ranked(i + 1, i == 0 ? null : compare(s, winner)));
		}
		return out;
	}

	/** Criterion-by-criterion comparison of {@code s} against the role's winner. */
	static Comparison compare(Suggestion s, Suggestion winner) {
		Map<String, ScoreItem> theirs = winner.breakdown()
			.stream()
			.collect(Collectors.toMap(ScoreItem::key, Function.identity()));
		record Diff(ScoreItem mine, ScoreItem theirs, int delta) {
		}
		List<Diff> diffs = s.breakdown()
			.stream()
			.filter(i -> theirs.containsKey(i.key()))
			.map(i -> new Diff(i, theirs.get(i.key()), i.points() - theirs.get(i.key()).points()))
			.filter(d -> d.delta() != 0)
			.toList();
		List<String> losses = diffs.stream()
			.filter(d -> d.delta() < 0)
			.sorted(Comparator.comparingInt(Diff::delta))
			.map(d -> "%s: %s, frente a %s (%d puntos)".formatted(d.mine().label(), d.mine().detail(),
					d.theirs().detail(), d.delta()))
			.toList();
		// points from a neutral default for unpublished data are never presented as an advantage
		List<Diff> realGains = diffs.stream()
			.filter(d -> d.delta() > 0 && d.mine().verified())
			.sorted(Comparator.comparingInt(Diff::delta).reversed())
			.toList();
		List<String> unknown = diffs.stream()
			.filter(d -> d.delta() > 0 && !d.mine().verified())
			.map(d -> lowerFirst(d.mine().label()))
			.toList();
		List<String> gains = realGains.stream()
			.map(d -> "%s: %s, frente a %s (+%d puntos)".formatted(d.mine().label(), d.mine().detail(),
					d.theirs().detail(), d.delta()))
			.toList();
		String lossLabels = labels(diffs.stream().filter(d -> d.delta() < 0).sorted(Comparator.comparingInt(Diff::delta))
			.map(d -> d.mine().label()).toList());
		String gainLabels = labels(realGains.stream().map(d -> d.mine().label()).toList());
		String verdict;
		if (s.score() == winner.score()) {
			verdict = "%s empata con %s a %d puntos; se ha priorizado la otra opción.".formatted(s.name(), winner.name(),
					s.score());
		}
		else if (gains.isEmpty()) {
			verdict = "%s suma %d puntos frente a %d de %s: no mejora en ningún criterio y pierde en %s."
				.formatted(s.name(), s.score(), winner.score(), winner.name(), lossLabels);
		}
		else {
			verdict = "%s suma %d puntos frente a %d de %s: lo que gana en %s no compensa lo que pierde en %s."
				.formatted(s.name(), s.score(), winner.score(), winner.name(), gainLabels, lossLabels);
		}
		if (!unknown.isEmpty()) {
			verdict += " Sus %s no están publicadas, así que no se cuentan como ventaja.".formatted(labels(unknown));
		}
		return new Comparison(winner.productId(), winner.name(), winner.score(), verdict, losses, gains);
	}

	private static String labels(List<String> labels) {
		List<String> lower = labels.stream().map(ProductMatcher::lowerFirst).toList();
		if (lower.size() <= 1) {
			return String.join("", lower);
		}
		return String.join(", ", lower.subList(0, lower.size() - 1)) + " y " + lower.getLast();
	}

	private static void addFeeCaution(List<String> cautions, Double fee, double monthly, Context ctx) {
		if (fee == null) {
			cautions.add("Comisiones no verificadas en su ficha pública: consúltalas antes de contratar.");
			return;
		}
		Double cost = feeCost(fee, monthly, ctx);
		cautions.add("Comisión total del %s %% al año: unos %s € menos al jubilarte que sin comisiones."
			.formatted(num(fee), eur(cost == null ? 0 : cost)));
	}

	/** Capital lost to fees by retirement: future value without fees minus with fees. */
	static Double feeCost(Double totalFeePct, double monthly, Context ctx) {
		if (totalFeePct == null || monthly <= 0) {
			return null;
		}
		double r = ctx.plan().rates().accumulationReal();
		return round(fv(monthly, r, ctx.years()) - fv(monthly, r - totalFeePct / 100, ctx.years()));
	}

	private static double fv(double monthly, double annualRate, double years) {
		int n = (int) Math.round(years * 12);
		double i = Math.pow(1 + annualRate, 1.0 / 12) - 1;
		return Math.abs(i) < 1e-12 ? monthly * n : monthly * (Math.pow(1 + i, n) - 1) / i;
	}

	private static double round(double v) {
		return Math.round(v * 100) / 100.0;
	}

	private static String eur(double v) {
		return String.format(Locale.forLanguageTag("es-ES"), "%,.0f", v);
	}

	private static String num(double v) {
		return String.format(Locale.forLanguageTag("es-ES"), "%.2f", v).replaceAll(",?0+$", "");
	}

	private static String profileName(RiskProfile p) {
		return switch (p) {
			case CONSERVATIVE -> "conservador";
			case BALANCED -> "moderado";
			case DYNAMIC -> "dinámico";
		};
	}

	private static String lowerFirst(String s) {
		return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

}
