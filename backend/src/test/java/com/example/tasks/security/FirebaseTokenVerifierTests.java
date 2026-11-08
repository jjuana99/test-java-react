package com.example.tasks.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirebaseTokenVerifierTests {

	private static final String PROJECT = "react-java-test";

	private final RSAKey googleKey;

	private final FirebaseTokenVerifier verifier;

	FirebaseTokenVerifierTests() throws Exception {
		this.googleKey = new RSAKeyGenerator(2048).keyID("k1").generate();
		this.verifier = new FirebaseTokenVerifier(PROJECT, new ImmutableJWKSet<>(new JWKSet(googleKey.toPublicJWK())));
	}

	private String token(RSAKey key, String audience, String issuer, Date expires) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("uid-1")
			.audience(audience)
			.issuer(issuer)
			.issueTime(new Date(System.currentTimeMillis() - 60_000))
			.expirationTime(expires)
			.claim("email", "persona@gmail.com")
			.claim("email_verified", true)
			.claim("name", "Persona")
			.claim("firebase", Map.of("sign_in_provider", "google.com"))
			.build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	private static Date inOneHour() {
		return new Date(System.currentTimeMillis() + 3_600_000);
	}

	@Test
	void acceptsValidFirebaseToken() throws Exception {
		FirebaseTokenVerifier.User user = verifier
			.verify(token(googleKey, PROJECT, "https://securetoken.google.com/" + PROJECT, inOneHour()));

		assertThat(user.email()).isEqualTo("persona@gmail.com");
		assertThat(user.emailVerified()).isTrue();
		assertThat(user.provider()).isEqualTo("google.com");
	}

	@Test
	void rejectsTokensOfAnotherProjectExpiredOrForged() throws Exception {
		String otherIssuer = "https://securetoken.google.com/otro-proyecto";
		assertThatThrownBy(() -> verifier.verify(token(googleKey, "otro-proyecto", otherIssuer, inOneHour())))
			.isInstanceOf(Exception.class);
		assertThatThrownBy(() -> verifier.verify(token(googleKey, PROJECT,
				"https://securetoken.google.com/" + PROJECT, new Date(System.currentTimeMillis() - 3_600_000))))
			.isInstanceOf(Exception.class);
		RSAKey forged = new RSAKeyGenerator(2048).keyID("k1").generate();
		assertThatThrownBy(() -> verifier
			.verify(token(forged, PROJECT, "https://securetoken.google.com/" + PROJECT, inOneHour())))
			.isInstanceOf(Exception.class);
	}

}
