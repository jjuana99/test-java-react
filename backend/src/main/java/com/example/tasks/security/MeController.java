package com.example.tasks.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who is calling: lets the frontend show the account and hide admin-only actions. */
@RestController
public class MeController {

	public record Me(String email, String name, boolean admin) {
	}

	@GetMapping("/api/me")
	public Me me(HttpServletRequest request) {
		FirebaseTokenVerifier.User user = (FirebaseTokenVerifier.User) request
			.getAttribute(ApiAuthFilter.USER_ATTRIBUTE);
		boolean admin = Boolean.TRUE.equals(request.getAttribute(ApiAuthFilter.ADMIN_ATTRIBUTE));
		return user == null ? new Me(null, null, admin) : new Me(user.email(), user.name(), admin);
	}

}
