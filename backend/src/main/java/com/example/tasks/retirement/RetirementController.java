package com.example.tasks.retirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Retirement planner API. Neither the uploaded PDF nor the personal data is persisted. */
@RestController
@RequestMapping("/api/retirement")
public class RetirementController {

	private final VidaLaboralParser parser;

	private final RetirementPlanner planner;

	private final MarketAssumptionsService assumptions;

	private final ProductMatcher products;

	private final ProductPlanBuilder productPlans;

	private final NeighbourhoodService neighbourhoods;

	public RetirementController(VidaLaboralParser parser, RetirementPlanner planner,
			MarketAssumptionsService assumptions, ProductMatcher products, ProductPlanBuilder productPlans,
			NeighbourhoodService neighbourhoods) {
		this.parser = parser;
		this.planner = planner;
		this.assumptions = assumptions;
		this.products = products;
		this.productPlans = productPlans;
		this.neighbourhoods = neighbourhoods;
	}

	@PostMapping("/work-history")
	public WorkHistory workHistory(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El fichero está vacío");
		}
		try {
			byte[] bytes = file.getBytes();
			if (bytes.length < 5 || !new String(bytes, 0, 5).equals("%PDF-")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El fichero no es un PDF");
			}
			return parser.parse(bytes);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
		}
		catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "No se ha podido leer el PDF");
		}
	}

	public record AddressRequest(@NotBlank @Size(max = 200) String address) {
	}

	/** POST so the address travels in the body and never ends up in access logs. */
	@PostMapping("/neighbourhood")
	public Neighbourhood neighbourhood(@Valid @RequestBody AddressRequest request) {
		return neighbourhoods.find(request.address().strip());
	}

	@PostMapping("/plan")
	public RetirementPlan plan(@Valid @RequestBody PlanRequest request) {
		LocalDate today = LocalDate.now();
		RetirementPlan plan = planner.plan(request, assumptions.load(request.sex()), today);
		BankProducts matched = products.match(request, plan, today);
		ProductPlan productPlan = productPlans.build(request, plan, matched);
		return plan.withProducts(matched.withPlannedAmounts(productPlan), productPlan);
	}

}
