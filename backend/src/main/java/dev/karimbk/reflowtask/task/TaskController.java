package dev.karimbk.reflowtask.task;

import java.net.URI;
import java.util.List;

import dev.karimbk.reflowtask.user.CurrentUser;
import dev.karimbk.reflowtask.user.User;
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
	List<TaskResponse> list(@CurrentUser User user) {
		return this.service.findAll(user.getId());
	}

	@GetMapping("/{id}")
	TaskResponse get(@CurrentUser User user, @PathVariable long id) {
		return this.service.findById(user.getId(), id);
	}

	@PostMapping
	ResponseEntity<TaskResponse> create(@CurrentUser User user, @Valid @RequestBody TaskRequest request) {
		TaskResponse created = this.service.create(user.getId(), request);
		return ResponseEntity.created(URI.create("/api/v1/tasks/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	TaskResponse update(@CurrentUser User user, @PathVariable long id, @Valid @RequestBody TaskRequest request) {
		return this.service.update(user.getId(), id, request);
	}

	@PatchMapping("/{id}/status")
	TaskResponse changeStatus(@CurrentUser User user, @PathVariable long id,
			@Valid @RequestBody TaskStatusRequest request) {
		return this.service.changeStatus(user.getId(), id, request.status());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@CurrentUser User user, @PathVariable long id) {
		this.service.delete(user.getId(), id);
		return ResponseEntity.noContent().build();
	}

}
