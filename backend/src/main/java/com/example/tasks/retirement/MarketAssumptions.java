package com.example.tasks.retirement;

import java.time.LocalDate;
import java.util.Map;

/**
 * Market and demographic inputs taken from the warehouse: the ECB AAA yield curve and the
 * Eurostat life-expectancy table for Spain.
 *
 * @param safeNominalRate 10-year AAA euro spot rate (fraction, e.g. 0.035)
 * @param longRate 30-year AAA euro spot rate, used to price a life annuity
 * @param ratesDate date of the yield-curve observation
 * @param lifeExpectancy remaining years by age for the requested sex
 * @param lifeTableYear year of the life table used
 */
public record MarketAssumptions(double safeNominalRate, double longRate, LocalDate ratesDate,
		Map<Integer, Double> lifeExpectancy, int lifeTableYear) {

	/** ECB medium-term inflation target, used to express everything in today's euros. */
	public static final double INFLATION = 0.02;

	/** Real return of the safe asset. */
	double safeRealRate() {
		return (1 + safeNominalRate) / (1 + INFLATION) - 1;
	}

	double longRealRate() {
		return (1 + longRate) / (1 + INFLATION) - 1;
	}

	/** Remaining life expectancy at {@code age}, clamped to the table's range. */
	double lifeExpectancyAt(int age) {
		int min = lifeExpectancy.keySet().stream().min(Integer::compare).orElseThrow();
		int max = lifeExpectancy.keySet().stream().max(Integer::compare).orElseThrow();
		return lifeExpectancy.get(Math.max(min, Math.min(max, age)));
	}

}
