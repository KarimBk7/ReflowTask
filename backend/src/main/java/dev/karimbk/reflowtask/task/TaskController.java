package dev.karimbk.reflowtask.task;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned from the first commit: a mobile client will pin to a version, and adding
 * versioning after clients exist is the expensive mistake.
 */
@RestController
@RequestMapping("/api/v1/tasks")
class TaskController {

	private final TaskService service;

	TaskController(TaskService service) {
		this.service = service;
	}

	@GetMapping
	List<TaskResponse> list() {
		return this.service.findAll();
	}

	@GetMapping("/{id}")
	TaskResponse get(@PathVariable long id) {
		return this.service.findById(id);
	}

	@PostMapping
	ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
		TaskResponse created = this.service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/tasks/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	TaskResponse update(@PathVariable long id, @Valid @RequestBody TaskRequest request) {
		return this.service.update(id, request);
	}

	@PatchMapping("/{id}/status")
	TaskResponse changeStatus(@PathVariable long id, @Valid @RequestBody TaskStatusRequest request) {
		return this.service.changeStatus(id, request.status());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable long id) {
		this.service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
