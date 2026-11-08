package com.example.tasks.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies Firebase Authentication ID tokens: RS256 signature against Google's published
 * keys (cached), issuer and audience of this Firebase project, and expiry.
 */
@Component
public class FirebaseTokenVerifier {

	private static final String JWKS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

	/** The authenticated person, taken from a valid token. */
	public record User(String uid, String email, boolean emailVerified, String name, String provider) {
	}

	private final DefaultJWTProcessor<SecurityContext> processor;

	@Autowired
	public FirebaseTokenVerifier(@Value("${app.auth.firebase-project:react-java-test}") String projectId)
			throws MalformedURLException {
		this(projectId, JWKSourceBuilder.create(URI.create(JWKS).toURL()).retrying(true).build());
	}

	FirebaseTokenVerifier(String projectId, JWKSource<SecurityContext> keys) {
		this.processor = new DefaultJWTProcessor<>();
		processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, null));
		processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
		processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(projectId,
				new JWTClaimsSet.Builder().issuer("https://securetoken.google.com/" + projectId).build(),
				Set.of("sub", "exp", "iat", "email")));
	}

	/** Returns the user of a valid token, or throws if the token is invalid or expired. */
	public User verify(String token) throws Exception {
		JWTClaimsSet claims = processor.process(token, null);
		Object firebase = claims.getClaim("firebase");
		String provider = firebase instanceof Map<?, ?> m ? String.valueOf(m.get("sign_in_provider")) : null;
		Boolean verified = claims.getBooleanClaim("email_verified");
		return new User(claims.getSubject(), claims.getStringClaim("email"), Boolean.TRUE.equals(verified),
				claims.getStringClaim("name"), provider);
	}

}
