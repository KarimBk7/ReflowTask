package dev.karimbk.reflowtask.task;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import dev.karimbk.reflowtask.common.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

	private final TaskRepository tasks;

	private final Clock clock;

	TaskService(TaskRepository tasks, Clock clock) {
		this.tasks = tasks;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> findAll() {
		return this.tasks.findAllByOrderByCreatedAtDesc().stream().map(TaskResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public TaskResponse findById(long id) {
		return TaskResponse.of(require(id));
	}

	@Transactional
	public TaskResponse create(TaskRequest request) {
		Task task = new Task(request.title(), request.description(), request.estimatedMinutes(),
				Task.toDeadline(request.deadlineDate(), request.deadlineTime()),
				request.deadlineTime() != null, request.priority(), LocalDateTime.now(this.clock));
		return TaskResponse.of(this.tasks.save(task));
	}

	@Transactional
	public TaskResponse update(long id, TaskRequest request) {
		Task task = require(id);
		task.setTitle(request.title());
		task.setDescription(request.description());
		task.setEstimatedMinutes(request.estimatedMinutes());
		task.setDeadline(Task.toDeadline(request.deadlineDate(), request.deadlineTime()),
				request.deadlineTime() != null);
		task.setPriority(request.priority());
		return TaskResponse.of(task);
	}

	@Transactional
	public TaskResponse changeStatus(long id, TaskStatus status) {
		Task task = require(id);
		task.setStatus(status);
		return TaskResponse.of(task);
	}

	@Transactional
	public void delete(long id) {
		this.tasks.delete(require(id));
	}

	private Task require(long id) {
		return this.tasks.findById(id).orElseThrow(() -> new NotFoundException("Task", id));
	}

}
