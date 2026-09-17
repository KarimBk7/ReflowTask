package dev.karimbk.reflowtask.task;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.schedule.RescheduleTrigger;
import dev.karimbk.reflowtask.schedule.SchedulerService;
import dev.karimbk.reflowtask.schedule.TimeBlock;
import dev.karimbk.reflowtask.schedule.TimeBlockRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

	private final TaskRepository tasks;

	private final TimeBlockRepository blocks;

	private final SchedulerService scheduler;

	private final Clock clock;

	TaskService(TaskRepository tasks, TimeBlockRepository blocks, SchedulerService scheduler, Clock clock) {
		this.tasks = tasks;
		this.blocks = blocks;
		this.scheduler = scheduler;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> findAll() {
		// One query for every block, not one per task.
		Map<Long, List<TimeBlock>> byTask = new HashMap<>();
		for (TimeBlock block : this.blocks.findAll()) {
			byTask.computeIfAbsent(block.getTask().getId(), (id) -> new ArrayList<>()).add(block);
		}
		return this.tasks.findAllByOrderByCreatedAtDesc()
			.stream()
			.map((task) -> respond(task, byTask.getOrDefault(task.getId(), List.of())))
			.toList();
	}

	@Transactional(readOnly = true)
	public TaskResponse findById(long id) {
		return respond(require(id));
	}

	/**
	 * Anything that changes what needs scheduling triggers a replan, so the calendar is
	 * correct the moment the user looks at it rather than at the next job tick.
	 *
	 * Responses are built after the replan, so they report where the task now sits rather than
	 * where it sat before this change.
	 */
	@Transactional
	public TaskResponse create(TaskRequest request) {
		LocalDateTime fixedStart = request.fixedStart();
		if (fixedStart != null) {
			// Checked before the task is saved, so a refused time leaves nothing behind.
			this.scheduler.assertCanFix(fixedStart, fixedStart.plusMinutes(request.estimatedMinutes()), null);
		}
		Task task = new Task(request.title(), request.description(), request.estimatedMinutes(),
				Task.toDeadline(request.deadlineDate(), request.deadlineTime()),
				request.deadlineTime() != null, request.priority(), LocalDateTime.now(this.clock));
		Task saved = this.tasks.save(task);
		if (fixedStart != null) {
			this.scheduler.fix(saved, fixedStart);
		}
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		return respond(saved);
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
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		return respond(task);
	}

	@Transactional
	public TaskResponse changeStatus(long id, TaskStatus status) {
		Task task = require(id);
		task.setStatus(status);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		return respond(task);
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

	private TaskResponse respond(Task task) {
		return respond(task, this.blocks.findByTaskId(task.getId()));
	}

	/**
	 * Placement is worked out from every block the task has, never from a date range. A client
	 * that derived it from the blocks it happened to be displaying would report any task placed
	 * outside that range as unscheduled.
	 */
	private static TaskResponse respond(Task task, List<TimeBlock> placed) {
		long minutes = 0;
		boolean atRisk = false;
		for (TimeBlock block : placed) {
			minutes += block.toSlot().minutes();
			if (task.getDeadline() != null && block.getEndAt().isAfter(task.getDeadline())) {
				atRisk = true;
			}
		}
		return TaskResponse.of(task, (int) minutes, atRisk);
	}

	private Task require(long id) {
		return this.tasks.findById(id).orElseThrow(() -> new NotFoundException("Task", id));
	}

}
