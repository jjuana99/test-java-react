package com.example.tasks.retirement.products;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Retirement products of a bank, loaded from {@code products/caixabank.json}. The file is
 * hand-maintained from the entity's public product pages; {@code null} fees mean "not
 * verified" and are never guessed.
 */
@Component
public class ProductCatalog {

	public enum Type {

		PENSION_PLAN, PACK, SAVINGS_INSURANCE, GUARANTEED_SAVINGS, ANNUITY

	}

	public record Variant(int targetYear, Integer risk, Double managementFee, Double depositFee, Double return12m) {
	}

	public record Product(String id, String name, Type type, String category, String description,
			List<Variant> variants, Integer risk, Integer riskScale, Double managementFee, Double depositFee,
			Double return12m, Double guaranteedRate, Double minimumRate, Double minMonthly, Double maxAnnual,
			Double minPremium, Boolean lifeCover, String liquidity, String taxBenefit, String url) {

		/** Total yearly fee in %, or null when not verified. */
		public Double totalFee() {
			return managementFee == null || depositFee == null ? null : managementFee + depositFee;
		}

	}

	public record Unavailable(String name, String reason) {
	}

	public record Catalog(String provider, LocalDate verifiedOn, String note, List<Product> products,
			List<Unavailable> unavailable) {

		public Product product(String id) {
			return products.stream()
				.filter(p -> p.id().equals(id))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Producto no encontrado: " + id));
		}

	}

	private final Catalog catalog;

	public ProductCatalog() throws IOException {
		JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
		try (InputStream in = new ClassPathResource("products/caixabank.json").getInputStream()) {
			this.catalog = mapper.readValue(in, Catalog.class);
		}
	}

	public Catalog catalog() {
		return catalog;
	}

}
