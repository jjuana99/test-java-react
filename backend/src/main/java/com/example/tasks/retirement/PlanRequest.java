package com.example.tasks.retirement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

/** What a person tells the planner about themselves. Nothing of it is stored. */
public record PlanRequest(@NotNull @Past LocalDate birthDate, @NotNull Sex sex, @NotNull Employment employment,
		@NotNull @DecimalMin("0") @DecimalMax("10000000") Double grossAnnualSalary,
		@NotNull @DecimalMin("0") @DecimalMax("60") Double contributionYears, Boolean keepsContributing,
		@Min(55) @Max(75) Integer retirementAge, @DecimalMin("0") Double currentSavings,
		@DecimalMin("10") @DecimalMax("150") Double targetReplacementPct, @NotNull RiskProfile riskProfile,
		@Min(0) @Max(20) Integer dependents, @DecimalMin("-3") @DecimalMax("5") Double realWageGrowthPct) {

	/** Default real (above inflation) yearly salary growth. */
	static final double DEFAULT_WAGE_GROWTH = 0.005;

	public enum Sex {

		FEMALE, MALE, UNSPECIFIED

	}

	public enum Employment {

		EMPLOYEE, SELF_EMPLOYED, CIVIL_SERVANT, UNEMPLOYED, INACTIVE

	}

	public enum RiskProfile {

		CONSERVATIVE, BALANCED, DYNAMIC

	}

	boolean contributing() {
		return keepsContributing != null ? keepsContributing
				: employment == Employment.EMPLOYEE || employment == Employment.SELF_EMPLOYED
						|| employment == Employment.CIVIL_SERVANT;
	}

	double savings() {
		return currentSavings == null ? 0 : currentSavings;
	}

	double replacement() {
		return (targetReplacementPct == null ? 75 : targetReplacementPct) / 100;
	}

	int dependentsCount() {
		return dependents == null ? 0 : dependents;
	}

	double wageGrowth() {
		return realWageGrowthPct == null ? DEFAULT_WAGE_GROWTH : realWageGrowthPct / 100;
	}

	/** Gross salary {@code years} from now in today's euros (negative years look back). */
	double salaryIn(double years) {
		return grossAnnualSalary * Math.pow(1 + wageGrowth(), years);
	}

}
