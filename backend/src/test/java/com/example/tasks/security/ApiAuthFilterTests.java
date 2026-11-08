package com.example.tasks.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = { "app.auth.enabled=true", "app.admin.key=clave-scheduler",
		"app.admin.emails=Admin@gmail.com" })
@AutoConfigureMockMvc
class ApiAuthFilterTests {

	private static final String RULE = """
			{"name":"x","seriesCode":"no.existe","condition":"ABOVE","threshold":1,"severity":"INFO"}""";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private FirebaseTokenVerifier verifier;

	@BeforeEach
	void tokens() throws Exception {
		willThrow(new IllegalArgumentException("bad token")).given(verifier).verify(anyString());
		willReturn(new FirebaseTokenVerifier.User("1", "persona@gmail.com", true, "P", "google.com")).given(verifier)
			.verify("user");
		willReturn(new FirebaseTokenVerifier.User("2", "admin@gmail.com", true, "A", "google.com")).given(verifier)
			.verify("admin");
		willReturn(new FirebaseTokenVerifier.User("3", "ana@empresa.es", true, "E", "google.com")).given(verifier)
			.verify("empresa");
		willReturn(new FirebaseTokenVerifier.User("4", "x@gmail.com", false, "X", "google.com")).given(verifier)
			.verify("noverificado");
	}

	@Test
	void everyApiCallNeedsASession() {
		assertThat(mvc.get().uri("/api/sources")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/retirement/plan").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/sources").header("Authorization", "Bearer basura"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void onlyVerifiedGmailAccountsGetIn() {
		assertThat(mvc.get().uri("/api/sources").header("Authorization", "Bearer user")).hasStatusOk();
		assertThat(mvc.get().uri("/api/sources").header("Authorization", "Bearer empresa"))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/sources").header("Authorization", "Bearer noverificado"))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void adminActionsNeedAnAdminEmailOrTheSchedulerKey() {
		assertThat(mvc.post().uri("/api/alert-rules").header("Authorization", "Bearer user")
			.contentType(MediaType.APPLICATION_JSON).content(RULE)).hasStatus(HttpStatus.FORBIDDEN);
		// admin email (case-insensitive) passes the filter and reaches validation
		assertThat(mvc.post().uri("/api/alert-rules").header("Authorization", "Bearer admin")
			.contentType(MediaType.APPLICATION_JSON).content(RULE)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.post().uri("/api/alert-rules").header(ApiAuthFilter.ADMIN_KEY_HEADER, "clave-scheduler")
			.contentType(MediaType.APPLICATION_JSON).content(RULE)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void meReportsTheAccountAndRole() {
		assertThat(mvc.get().uri("/api/me").header("Authorization", "Bearer admin")).bodyJson()
			.extractingPath("$.admin")
			.isEqualTo(true);
		assertThat(mvc.get().uri("/api/me").header("Authorization", "Bearer user")).bodyJson()
			.extractingPath("$.email")
			.isEqualTo("persona@gmail.com");
	}

	@Test
	void requiredAdminKeyFailsFastWhenMissing() {
		assertThatThrownBy(() -> new ApiAuthFilter("", true, true, "gmail.com", "", verifier))
			.isInstanceOf(IllegalStateException.class);
	}

}
