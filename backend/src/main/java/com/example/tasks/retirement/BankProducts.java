package com.example.tasks.retirement;

import com.example.tasks.retirement.products.ProductCatalog;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bank products matched to a retirement plan, grouped by the role they play in it. */
public record BankProducts(String provider, LocalDate verifiedOn, String note, String summary,
		List<Suggestion> suggestions, List<ProductCatalog.Unavailable> unavailable) {

	/**
	 * Replaces the monthly amount of the recommended products with the ones of the product
	 * plan, which accounts for each product's return and fees.
	 */
	BankProducts withPlannedAmounts(ProductPlan plan) {
		if (plan == null) {
			return this;
		}
		Map<String, Double> amounts = new HashMap<>();
		plan.holdings().forEach(h -> amounts.put(h.productId(), h.monthly()));
		List<Suggestion> updated = suggestions.stream()
			.map(s -> s.recommended() && amounts.containsKey(s.productId()) ? s.withMonthly(amounts.get(s.productId())) : s)
			.toList();
		return new BankProducts(provider, verifiedOn, note, summary, updated, unavailable);
	}

	public enum Role {

		PENSION_PLAN("Plan de pensiones (con ventaja fiscal)"), SAVINGS("Ahorro complementario"),
		LUMP_SUM("Para tu ahorro actual"), PAYOUT("Al jubilarte");

		private final String label;

		Role(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}

	}

	/**
	 * Points a product earns on one criterion. Every product of a role is scored on the same
	 * criteria, so breakdowns can be compared item by item.
	 */
	public record ScoreItem(String key, String label, int points, int max, String detail, boolean verified) {

		/** {@code verified} is false when the points come from a neutral default for missing data. */
		public ScoreItem(String key, String label, int points, int max, String detail) {
			this(key, label, points, max, detail, true);
		}

	}

	/** Why a product ranks below the top one of its role. */
	public record Comparison(String winnerId, String winnerName, int winnerScore, String verdict,
			List<String> losses, List<String> gains) {
	}

	/**
	 * One product evaluated for one role. {@code rank} starts at 1 within the role,
	 * {@code recommended} marks rank 1, {@code best} the single best product of the whole
	 * plan, and {@code versusBest} explains every lower-ranked product against rank 1.
	 */
	public record Suggestion(String productId, String name, ProductCatalog.Type type, Role role, String roleLabel,
			int score, int rank, boolean recommended, boolean best, Double monthlyAmount, Integer risk,
			Integer riskScale, Double totalFee, Double feeCostAtRetirement, Double return12m, List<String> reasons,
			List<String> cautions, List<ScoreItem> breakdown, Comparison versusBest, String description, String url) {

		Suggestion ranked(int rank, Comparison versusBest) {
			return new Suggestion(productId, name, type, role, roleLabel, score, rank, rank == 1, best, monthlyAmount,
					risk, riskScale, totalFee, feeCostAtRetirement, return12m, reasons, cautions, breakdown,
					versusBest, description, url);
		}

		Suggestion withMonthly(Double monthly) {
			return new Suggestion(productId, name, type, role, roleLabel, score, rank, recommended, best, monthly, risk,
					riskScale, totalFee, feeCostAtRetirement, return12m, reasons, cautions, breakdown, versusBest,
					description, url);
		}

		Suggestion asBest() {
			return new Suggestion(productId, name, type, role, roleLabel, score, rank, recommended, true,
					monthlyAmount, risk, riskScale, totalFee, feeCostAtRetirement, return12m, reasons, cautions,
					breakdown, versusBest, description, url);
		}

	}

}
