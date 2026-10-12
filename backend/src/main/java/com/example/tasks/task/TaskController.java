package com.example.tasks.task;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskRepository repository;

	public TaskController(TaskRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<Task> list() {
		return repository.findAllByOrderByCreatedAtDesc();
	}

	@GetMapping("/{id}")
	public Task get(@PathVariable Long id) {
		return find(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Task create(@Valid @RequestBody TaskRequest request) {
		Task task = new Task(request.title().strip());
		task.setDone(Boolean.TRUE.equals(request.done()));
		return repository.save(task);
	}

	@PutMapping("/{id}")
	@Transactional
	public Task update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
		Task task = find(id);
		task.setTitle(request.title().strip());
		if (request.done() != null) {
			task.setDone(request.done());
		}
		return task;
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		repository.delete(find(id));
	}

	private Task find(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task " + id + " not found"));
	}

}
