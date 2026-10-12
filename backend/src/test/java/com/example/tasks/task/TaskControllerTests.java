package com.example.tasks.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void crudLifecycle() throws Exception {
		var created = mvc.post().uri("/api/tasks")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"  Comprar pan  \"}")
			.exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);
		assertThat(created).bodyJson().extractingPath("$.title").isEqualTo("Comprar pan");
		Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

		var updated = mvc.put().uri("/api/tasks/{id}", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Comprar pan\",\"done\":true}")
			.exchange();
		assertThat(updated).hasStatusOk();
		assertThat(updated).bodyJson().extractingPath("$.done").isEqualTo(true);

		assertThat(mvc.delete().uri("/api/tasks/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri("/api/tasks/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectsBlankTitle() {
		assertThat(mvc.post().uri("/api/tasks")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\" \"}")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
