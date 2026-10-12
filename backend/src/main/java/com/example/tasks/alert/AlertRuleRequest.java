package com.example.tasks.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlertRuleRequest(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 120) String seriesCode,
		@NotNull AlertRule.Condition condition, @NotNull Double threshold, @NotNull AlertRule.Severity severity,
		@Min(1) @Max(366) Integer lookback, Boolean enabled) {
}
