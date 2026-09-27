package dev.karimbk.reflowtask.task;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.karimbk.reflowtask.common.BadRequestException;
import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.schedule.BlockState;
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
	public List<TaskResponse> findAll(long userId) {
		// One query for every block of this user's tasks, not one per task.
		Map<Long, List<TimeBlock>> byTask = new HashMap<>();
		for (TimeBlock block : this.blocks.findAllWithTaskByUserId(userId)) {
			byTask.computeIfAbsent(block.getTask().getId(), (id) -> new ArrayList<>()).add(block);
		}
		LocalDateTime now = LocalDateTime.now(this.clock);
		return this.tasks.findByUserIdOrderByCreatedAtDesc(userId)
			.stream()
			.map((task) -> respond(task, byTask.getOrDefault(task.getId(), List.of()), now))
			.toList();
	}

	@Transactional(readOnly = true)
	public TaskResponse findById(long userId, long id) {
		return respond(require(userId, id));
	}

	/**
	 * Anything that changes what needs scheduling triggers a replan, so the calendar is
	 * correct the moment the user looks at it rather than at the next job tick.
	 *
	 * Responses are built after the replan, so they report where the task now sits rather than
	 * where it sat before this change.
	 */
	@Transactional
	public TaskResponse create(long userId, TaskRequest request) {
		LocalDateTime fixedStart = request.fixedStart();
		if (fixedStart != null) {
			// Checked before the task is saved, so a refused time leaves nothing behind.
			this.scheduler.assertCanFix(userId, fixedStart, fixedStart.plusMinutes(request.estimatedMinutes()), null);
		}
		Task task = new Task(userId, request.title(), request.description(), request.estimatedMinutes(),
				Task.toDeadline(request.deadlineDate(), request.deadlineTime()),
				request.deadlineTime() != null, request.priority(), LocalDateTime.now(this.clock));
		task.setRecurrence(request.recurrence());
		task.setNotBefore(request.notBefore());
		task.setProfile(request.profile());
		task.setAfterTaskId(checkedPredecessor(userId, null, request.afterTaskId()));
		Task saved = this.tasks.save(task);
		if (fixedStart != null) {
			this.scheduler.fix(userId, saved, fixedStart);
		}
		this.scheduler.replan(userId, RescheduleTrigger.TASK_CHANGED);
		return respond(saved);
	}

	@Transactional
	public TaskResponse update(long userId, long id, TaskRequest request) {
		Task task = require(userId, id);
		task.setTitle(request.title());
		task.setDescription(request.description());
		task.setEstimatedMinutes(request.estimatedMinutes());
		task.setDeadline(Task.toDeadline(request.deadlineDate(), request.deadlineTime()),
				request.deadlineTime() != null);
		task.setPriority(request.priority());
		task.setRecurrence(request.recurrence());
		task.setNotBefore(request.notBefore());
		task.setProfile(request.profile());
		task.setAfterTaskId(checkedPredecessor(userId, id, request.afterTaskId()));
		this.scheduler.replan(userId, RescheduleTrigger.TASK_CHANGED);
		return respond(task);
	}

	@Transactional
	public TaskResponse changeStatus(long userId, long id, TaskStatus status) {
		Task task = require(userId, id);
		task.setStatus(status);
		this.scheduler.replan(userId, RescheduleTrigger.TASK_CHANGED);
		return respond(task);
	}

	@Transactional
	public void delete(long userId, long id) {
		Task task = require(userId, id);
		// The task's blocks must go before the task does: the database would cascade them,
		// but Hibernate cannot see that and the replan below would trip over the leftovers.
		this.scheduler.releaseBlocksOf(id);
		this.tasks.delete(task);
		this.tasks.flush();
		this.scheduler.replan(userId, RescheduleTrigger.TASK_CHANGED);
	}

	private TaskResponse respond(Task task) {
		return respond(task, this.blocks.findByTaskId(task.getId()), LocalDateTime.now(this.clock));
	}

	/**
	 * Placement is worked out from every block the task has, never from a date range. A client
	 * that derived it from the blocks it happened to be displaying would report any task placed
	 * outside that range as unscheduled.
	 */
	private static TaskResponse respond(Task task, List<TimeBlock> placed, LocalDateTime now) {
		long minutes = 0;
		long done = 0;
		boolean atRisk = false;
		LocalDateTime next = null;
		for (TimeBlock block : placed) {
			if (block.isPlanned() && !block.hasEndedBy(now) && (next == null || block.getStartAt().isBefore(next))) {
				next = block.getStartAt();
			}
			// A missed part is a record of time that was not used; the work is planned again elsewhere.
			if (block.getState() == BlockState.MISSED) {
				continue;
			}
			minutes += block.toSlot().minutes();
			if (block.getState() == BlockState.DONE) {
				done += block.toSlot().minutes();
			}
			else if (task.getDeadline() != null && block.getEndAt().isAfter(task.getDeadline())) {
				atRisk = true;
			}
		}
		return TaskResponse.of(task, (int) minutes, (int) done, atRisk, next);
	}

	/**
	 * The task to wait for, if it is the user's own and waiting for it does not come back round to
	 * this task: A after B after A would leave both waiting forever.
	 */
	private Long checkedPredecessor(long userId, Long taskId, Long afterTaskId) {
		if (afterTaskId == null) {
			return null;
		}
		Task predecessor = this.tasks.findByIdAndUserId(afterTaskId, userId)
			.orElseThrow(() -> new BadRequestException("The task to wait for does not exist."));
		for (Task step = predecessor; step != null; step = (step.getAfterTaskId() == null) ? null
				: this.tasks.findByIdAndUserId(step.getAfterTaskId(), userId).orElse(null)) {
			if (step.getId().equals(taskId)) {
				throw new BadRequestException("These tasks would wait for each other.");
			}
		}
		return afterTaskId;
	}

	private Task require(long userId, long id) {
		return this.tasks.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Task", id));
	}

}
