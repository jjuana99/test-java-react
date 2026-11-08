package com.example.tasks.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the whole {@code /api}. With {@code app.auth.enabled} every request needs a valid
 * Firebase ID token (Google sign-in) of a verified address in the allowed domain, and the
 * administrative endpoints also need the address to be in {@code app.admin.emails}. The
 * {@code X-Admin-Key} header grants admin access to machine callers such as Cloud
 * Scheduler. With authentication disabled (local development) only the admin key, when
 * configured, is enforced on administrative endpoints.
 */
@Component
public class ApiAuthFilter extends OncePerRequestFilter {

	public static final String ADMIN_KEY_HEADER = "X-Admin-Key";

	public static final String USER_ATTRIBUTE = ApiAuthFilter.class.getName() + ".user";

	public static final String ADMIN_ATTRIBUTE = ApiAuthFilter.class.getName() + ".admin";

	private final byte[] adminKey;

	private final boolean authEnabled;

	private final String allowedDomain;

	private final Set<String> adminEmails;

	private final FirebaseTokenVerifier verifier;

	public ApiAuthFilter(@Value("${app.admin.key:}") String adminKey,
			@Value("${app.admin.required:false}") boolean adminKeyRequired,
			@Value("${app.auth.enabled:false}") boolean authEnabled,
			@Value("${app.auth.allowed-domain:gmail.com}") String allowedDomain,
			@Value("${app.admin.emails:}") String adminEmails, FirebaseTokenVerifier verifier) {
		if (adminKeyRequired && adminKey.isBlank()) {
			throw new IllegalStateException("app.admin.key es obligatoria en este entorno (variable ADMIN_KEY)");
		}
		this.adminKey = adminKey.isBlank() ? null : adminKey.getBytes(StandardCharsets.UTF_8);
		this.authEnabled = authEnabled;
		this.allowedDomain = allowedDomain.toLowerCase(Locale.ROOT);
		this.adminEmails = Arrays.stream(adminEmails.split(","))
			.map(e -> e.strip().toLowerCase(Locale.ROOT))
			.filter(e -> !e.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
		this.verifier = verifier;
	}

	static boolean isAdminRequest(String method, String path) {
		if (path.startsWith("/api/sources/") && "POST".equals(method)) {
			return true;
		}
		if (path.startsWith("/api/alert-rules") && !"GET".equals(method)) {
			return true;
		}
		return path.startsWith("/api/alerts/") && "POST".equals(method);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		boolean adminRequest = isAdminRequest(request.getMethod(), request.getRequestURI());
		if (validAdminKey(request.getHeader(ADMIN_KEY_HEADER))) {
			request.setAttribute(ADMIN_ATTRIBUTE, true);
			chain.doFilter(request, response);
			return;
		}
		if (!authEnabled) {
			if (adminRequest && adminKey != null) {
				reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Se necesita la clave de administrador");
				return;
			}
			request.setAttribute(ADMIN_ATTRIBUTE, adminKey == null);
			chain.doFilter(request, response);
			return;
		}

		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Inicia sesión con tu cuenta de Google");
			return;
		}
		FirebaseTokenVerifier.User user;
		try {
			user = verifier.verify(header.substring("Bearer ".length()).strip());
		}
		catch (Exception e) {
			reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Tu sesión no es válida o ha caducado");
			return;
		}
		String email = user.email() == null ? "" : user.email().toLowerCase(Locale.ROOT);
		if (!user.emailVerified() || !email.endsWith("@" + allowedDomain)) {
			reject(response, HttpServletResponse.SC_FORBIDDEN,
					"Solo se admiten cuentas @%s verificadas".formatted(allowedDomain));
			return;
		}
		boolean admin = adminEmails.contains(email);
		if (adminRequest && !admin) {
			reject(response, HttpServletResponse.SC_FORBIDDEN, "Tu cuenta no tiene permisos de administrador");
			return;
		}
		request.setAttribute(USER_ATTRIBUTE, user);
		request.setAttribute(ADMIN_ATTRIBUTE, admin);
		chain.doFilter(request, response);
	}

	private boolean validAdminKey(String provided) {
		return adminKey != null && provided != null
				&& MessageDigest.isEqual(adminKey, provided.getBytes(StandardCharsets.UTF_8));
	}

	private static void reject(HttpServletResponse response, int status, String detail) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"detail\":\"" + detail.replace("\"", "'") + "\"}");
	}

}
