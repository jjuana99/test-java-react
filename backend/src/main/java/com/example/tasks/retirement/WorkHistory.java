package com.example.tasks.retirement;

import java.time.LocalDate;
import java.util.List;

/**
 * What the planner needs from an "Informe de vida laboral". Identity data (name, NSS,
 * document number) is deliberately never extracted; the home {@code address} is, so the
 * person's neighbourhood can be looked up, and it is returned only to them, never stored.
 */
public record WorkHistory(LocalDate reportDate, int totalDays, int years, int months, int days,
		double contributionYears, LocalDate firstRegistration, String currentRegime, boolean currentlyRegistered,
		String address, List<Period> periods, List<String> warnings) {

	/** One row of the "situaciones" table; {@code to} is null while the situation is still open. */
	public record Period(String regime, String employer, LocalDate from, LocalDate to, int days, boolean assimilated) {
	}

}
