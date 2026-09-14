package dev.karimbk.reflowtask.task;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.schedule.RescheduleTrigger;
import dev.karimbk.reflowtask.schedule.SchedulerService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

	private final TaskRepository tasks;

	private final SchedulerService scheduler;

	private final Clock clock;

	TaskService(TaskRepository tasks, SchedulerService scheduler, Clock clock) {
		this.tasks = tasks;
		this.scheduler = scheduler;
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

	/**
	 * Anything that changes what needs scheduling triggers a replan, so the calendar is
	 * correct the moment the user looks at it rather than at the next job tick.
	 */
	@Transactional
	public TaskResponse create(TaskRequest request) {
		Task task = new Task(request.title(), request.description(), request.estimatedMinutes(),
				Task.toDeadline(request.deadlineDate(), request.deadlineTime()),
				request.deadlineTime() != null, request.priority(), LocalDateTime.now(this.clock));
		TaskResponse created = TaskResponse.of(this.tasks.save(task));
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		return created;
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
		TaskResponse updated = TaskResponse.of(task);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		return updated;
	}

	@Transactional
	public TaskResponse changeStatus(long id, TaskStatus status) {
		Task task = require(id);
		task.setStatus(status);
		TaskResponse changed = TaskResponse.of(task);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		return changed;
	}

	@Transactional
	public void delete(long id) {
		Task task = require(id);
		// The task's blocks must go before the task does: the database would cascade them,
		// but Hibernate cannot see that and the replan below would trip over the leftovers.
		this.scheduler.releaseBlocksOf(id);
		this.tasks.delete(task);
		this.tasks.flush();
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
	}

	private Task require(long id) {
		return this.tasks.findById(id).orElseThrow(() -> new NotFoundException("Task", id));
	}

}
