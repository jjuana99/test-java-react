package com.example.tasks.retirement;

import java.util.List;

/**
 * Household typologies (Habits) of the census section that contains an address, with the
 * province and national shares for comparison. Shares are percentages of the classified
 * families (typologies A-R).
 */
public record Neighbourhood(String address, String municipality, String province, String postalCode, String cusec,
		int year, long families, List<TypologyShare> typologies) {

	public record TypologyShare(String code, String name, String group, long families, double share,
			double provinceShare, double nationalShare) {
	}

}
