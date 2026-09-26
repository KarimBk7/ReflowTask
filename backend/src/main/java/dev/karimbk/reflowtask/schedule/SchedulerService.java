package dev.karimbk.reflowtask.schedule;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.karimbk.reflowtask.calendar.ExternalBusy;
import dev.karimbk.reflowtask.calendar.ExternalBusyRepository;
import dev.karimbk.reflowtask.common.ConflictException;
import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.config.SchedulingConfigProvider;
import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.Recurrence;
import dev.karimbk.reflowtask.task.Task;
import dev.karimbk.reflowtask.task.TaskRepository;
import dev.karimbk.reflowtask.task.TaskStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the planner's decisions into persisted blocks, and records what changed.
 *
 * Every entry point takes the acting user's id and never crosses that boundary: every lookup
 * below is scoped to that user's own tasks and blocks, so one household member's fixed
 * appointment can never collide with or be moved by another's.
 *
 * The rules about which existing blocks survive a replan live here, and they are the part
 * users actually feel:
 *
 * <ul>
 * <li>Blocks of completed tasks that have already started are history and never touched.
 * Future blocks of a completed task are removed, so finishing early frees the time.</li>
 * <li>A block the user is currently inside is left alone. Moving the thing someone is
 * working on right now would be hostile, pinned or not.</li>
 * <li>A block whose time has passed while its task is unfinished is a miss: it is kept as a
 * MISSED record, and the task is replanned. The record lets the person say later that the part
 * was done after all.</li>
 * <li>A part marked done counts against the estimate, so only the rest is planned.</li>
 * <li>Future pinned blocks are obstacles. That is what pinning means.</li>
 * <li>Every other future block is rebuilt from scratch. That is what replanning means.</li>
 * </ul>
 */
@Service
public class SchedulerService {

	/** Matches the ceiling a task's estimate may be given through the API. */
	private static final long MAX_ESTIMATE_MINUTES = 43_200;

	private final TaskRepository tasks;

	private final TimeBlockRepository blocks;

	private final RescheduleEventRepository events;

	private final SchedulingConfigProvider config;

	private final ExternalBusyRepository busy;

	private final Clock clock;

	SchedulerService(TaskRepository tasks, TimeBlockRepository blocks, RescheduleEventRepository events,
			SchedulingConfigProvider config, ExternalBusyRepository busy, Clock clock) {
		this.tasks = tasks;
		this.blocks = blocks;
		this.events = events;
		this.config = config;
		this.busy = busy;
		this.clock = clock;
	}

	/**
	 * Refuses to fix work at a time the schedule cannot give it. Called before anything is
	 * written, so a refused request leaves no task behind without its block.
	 *
	 * @param ignoreBlockId the block being moved, which may of course overlap its own old place
	 */
	@Transactional(readOnly = true)
	public void assertCanFix(long userId, LocalDateTime start, LocalDateTime end, Long ignoreBlockId) {
		if (!end.isAfter(LocalDateTime.now(this.clock))) {
			throw new ConflictException("Work cannot be fixed at a time that has already passed.");
		}
		boolean clash = this.blocks.findOverlappingForUser(userId, start, end)
			.stream()
			.anyMatch((block) -> block.isPinned() && !block.getId().equals(ignoreBlockId));
		if (clash) {
			throw new ConflictException("That time overlaps something that is already fixed.");
		}
	}

	/** Fixes a task at a chosen time with a pinned block covering its whole estimate. */
	@Transactional
	public void fix(long userId, Task task, LocalDateTime start) {
		LocalDateTime end = start.plusMinutes(task.getEstimatedMinutes());
		assertCanFix(userId, start, end, null);
		this.blocks.save(new TimeBlock(task, start, end, true));
	}

	/**
	 * Moves or resizes a block to where the user dragged it, pins it there, and replans the rest.
	 *
	 * Resizing changes the task's estimate by exactly the change in length: stretching a block
	 * says the work takes longer. Moving without resizing leaves the estimate alone.
	 */
	@Transactional
	public TimeBlock move(long userId, long blockId, LocalDateTime start, LocalDateTime end) {
		TimeBlock block = this.blocks.findByIdAndTaskUserId(blockId, userId)
			.orElseThrow(() -> new NotFoundException("Time block", blockId));
		LocalDateTime now = LocalDateTime.now(this.clock);
		if (block.getTask().getStatus() == TaskStatus.DONE) {
			throw new ConflictException("A completed task's time cannot be moved.");
		}
		if (block.hasEndedBy(now)) {
			throw new ConflictException("Time that has already passed cannot be moved.");
		}
		if (!block.isPlanned()) {
			throw new ConflictException("A part that is done or missed cannot be moved.");
		}
		assertCanFix(userId, start, end, blockId);

		long change = Duration.between(start, end).toMinutes()
				- Duration.between(block.getStartAt(), block.getEndAt()).toMinutes();
		Task task = block.getTask();
		task.setEstimatedMinutes((int) Math.clamp(task.getEstimatedMinutes() + change, 1, MAX_ESTIMATE_MINUTES));

		// Captured before the move: by the time replan() takes its own "before" snapshot, this
		// block's row already holds the new position, since it is saved and flushed first. Without
		// this override, a drag that needs nothing else to shift would compare the new position
		// against itself, see no change, and go unrecorded - and the board's ghost of an earlier,
		// unrelated move would keep showing long after this block had moved again.
		LocalDateTime previousStart = block.getStartAt();
		block.moveTo(start, end);
		this.blocks.flush();
		replan(userId, RescheduleTrigger.MANUAL, Map.of(task.getId(), List.of(previousStart)));
		return block;
	}

	/**
	 * Seeds one example task with a block that has already ended, unfinished - so the very next
	 * replan (config's onboarding save triggers one right after this runs) demonstrates the
	 * reflow itself: a "Missed" mark and the task placed again, live, on the one thing this
	 * product is named for, instead of making a first-run owner wait for a real miss to ever
	 * see it happen.
	 */
	@Transactional
	public void seedDemoMiss(long userId) {
		LocalDateTime now = LocalDateTime.now(this.clock);
		Task demo = this.tasks
			.save(new Task(userId, "See how this works: I was missed", null, 30, null, false, Priority.MEDIUM, now));
		this.blocks.save(new TimeBlock(demo, now.minusMinutes(45), now.minusMinutes(15), false));
	}

	/**
	 * Marks one part of a task done. Its minutes then count against the estimate, so the replan
	 * that follows places only what is left; when nothing is left, the task itself is done.
	 *
	 * Only a part that has started can be done. A part still ahead has no time of its own to keep
	 * as a record, and inventing one would put work on the calendar when it never happened; work
	 * done ahead of plan is the task marked done, or its estimate shortened.
	 */
	@Transactional
	public Optional<RescheduleEvent> completeBlock(long userId, long blockId) {
		TimeBlock block = ownedBlock(userId, blockId);
		if (block.getState() == BlockState.DONE) {
			return Optional.empty();
		}
		if (block.getStartAt().isAfter(LocalDateTime.now(this.clock))) {
			throw new ConflictException(
					"A part that has not started yet cannot be marked done. Mark the task done, or shorten it.");
		}
		block.markDone();
		this.blocks.flush();
		Task task = block.getTask();
		if (task.getStatus() != TaskStatus.DONE && doneMinutes(task) >= task.getEstimatedMinutes()) {
			task.setStatus(TaskStatus.DONE);
		}
		return replan(userId, RescheduleTrigger.TASK_CHANGED);
	}

	/** Undoes {@link #completeBlock}: the part is open again, and so is its task if it no longer adds up. */
	@Transactional
	public Optional<RescheduleEvent> reopenBlock(long userId, long blockId) {
		TimeBlock block = ownedBlock(userId, blockId);
		if (block.getState() != BlockState.DONE) {
			return Optional.empty();
		}
		block.reopen();
		this.blocks.flush();
		Task task = block.getTask();
		if (task.getStatus() == TaskStatus.DONE && doneMinutes(task) < task.getEstimatedMinutes()) {
			task.setStatus(TaskStatus.OPEN);
		}
		return replan(userId, RescheduleTrigger.TASK_CHANGED);
	}

	private TimeBlock ownedBlock(long userId, long blockId) {
		return this.blocks.findByIdAndTaskUserId(blockId, userId)
			.orElseThrow(() -> new NotFoundException("Time block", blockId));
	}

	private long doneMinutes(Task task) {
		return this.blocks.findByTaskId(task.getId())
			.stream()
			.filter((candidate) -> candidate.getState() == BlockState.DONE)
			.mapToLong((candidate) -> candidate.toSlot().minutes())
			.sum();
	}

	/**
	 * Drops every block belonging to a task that is about to be deleted.
	 *
	 * The database cascades this itself, but Hibernate cannot see a database-level cascade:
	 * the blocks would linger in the session still pointing at a removed task, and the next
	 * replan would try to persist one. Call this before deleting the task.
	 */
	@Transactional
	public void releaseBlocksOf(long taskId) {
		this.blocks.deleteAll(this.blocks.findByTaskId(taskId));
		this.blocks.flush();
	}

	/**
	 * Rebuilds one user's schedule. Returns the recorded event, or empty when nothing actually
	 * changed — a replan that moved nothing is not news, and writing it anyway would bury
	 * the real events in noise.
	 */
	@Transactional
	public Optional<RescheduleEvent> replan(long userId, RescheduleTrigger trigger) {
		return replan(userId, trigger, Map.of());
	}

	/**
	 * @param manualBeforeOverrides the true pre-move position of a block a caller has already
	 * moved and flushed (see {@link #move}), keyed by task id. Without this, that task's own move
	 * would compare its new position against itself and look unchanged.
	 */
	private Optional<RescheduleEvent> replan(long userId, RescheduleTrigger trigger,
			Map<Long, List<LocalDateTime>> manualBeforeOverrides) {
		LocalDateTime now = LocalDateTime.now(this.clock);
		SchedulingConfig settings = this.config.current(userId);
		continueSeries(userId, settings, now);
		Disposition disposition = disposeOf(this.blocks.findAllWithTaskByUserId(userId), now);

		Map<Long, Task> byId = this.tasks.findByUserIdAndStatusNot(userId, TaskStatus.DONE)
			.stream()
			.collect(Collectors.toMap(Task::getId, Function.identity()));
		List<TimeBlock> counted = new ArrayList<>(disposition.obstacles());
		counted.addAll(disposition.doneHistory());
		Map<Long, Long> covered = minutesPerTask(counted);

		List<SchedulableTask> toPlan = byId.values()
			.stream()
			.map((task) -> toSchedulable(task, covered))
			.filter((candidate) -> candidate.minutesToPlace() > 0)
			.toList();

		Map<Long, List<LocalDateTime>> before = new HashMap<>(startsPerTask(disposition.stillPlanned()));
		before.putAll(manualBeforeOverrides);

		// Busy time read from other calendars is an obstacle like a pinned block, buffer included.
		List<TimeSlot> taken = new ArrayList<>(slotsOf(disposition.obstacles()));
		for (ExternalBusy external : this.busy.findOverlapping(userId, now, now.plusDays(settings.horizonDays() + 1))) {
			taken.add(new TimeSlot(external.getStartAt(), external.getEndAt()));
		}
		List<PlannedBlock> planned = SchedulePlanner.plan(toPlan, taken, settings, now);

		this.blocks.deleteAll(disposition.toRemove());
		this.blocks.flush();
		for (PlannedBlock block : planned) {
			Task task = byId.get(block.taskId());
			this.blocks.save(new TimeBlock(task, block.start(), block.end(), false));
		}

		// Only planned blocks describe where work sits. A part in flight that is already done is
		// history, and counting it on one side of the comparison only would read as a move.
		Map<Long, List<LocalDateTime>> after = startsAfter(
				disposition.obstacles().stream().filter(TimeBlock::isPlanned).toList(), planned);
		return record(userId, trigger, now, before, after, disposition, byId);
	}

	/**
	 * Creates the next occurrence of every repeating task that was finished, however it was
	 * finished: marked done, or done part by part. Doing it here, where every change ends up,
	 * means no path to "done" can forget it.
	 *
	 * The next one is due one step after the last. Steps that are already over are skipped, not
	 * owed: a daily task finished three days late comes back tomorrow, not three times today. A
	 * daily task also skips days without working hours, so it is not due on a day off.
	 */
	private void continueSeries(long userId, SchedulingConfig settings, LocalDateTime now) {
		for (Task finished : this.tasks.findByUserIdAndStatusAndRecurrenceNotNull(userId, TaskStatus.DONE)) {
			Recurrence rule = finished.getRecurrence();
			LocalDateTime deadline = rule.next(finished.getDeadline());
			while (!deadline.isAfter(now) || (rule == Recurrence.DAILY && isDayOff(settings, deadline))) {
				deadline = rule.next(deadline);
			}
			this.tasks.save(finished.nextOccurrence(deadline, now));
		}
	}

	/** A day with no working hours, unless no day has any, when nothing would ever be due. */
	private static boolean isDayOff(SchedulingConfig settings, LocalDateTime date) {
		return settings.workingHoursOn(date.getDayOfWeek()).isEmpty() && !settings.workingHours().isEmpty();
	}

	// --- deciding what survives ------------------------------------------------------

	/**
	 * @param obstacles blocks that keep their time and consume capacity
	 * @param doneHistory parts marked done whose time is over: they count against the estimate
	 * but no longer occupy any capacity
	 * @param toRemove blocks being deleted, superseded or freed
	 * @param stillPlanned blocks that counted as "the plan" before this run, so a move can
	 * be detected
	 * @param missedStarts earliest missed start per task
	 * @param completedTaskIds tasks finished in the meantime. Releasing their future time is
	 * not a scheduling event, so they are left out of the history entirely.
	 * @param titles every task seen through a block, so the history can name a task whatever
	 * its status
	 */
	private record Disposition(List<TimeBlock> obstacles, List<TimeBlock> doneHistory, List<TimeBlock> toRemove,
			List<TimeBlock> stillPlanned,
			Map<Long, LocalDateTime> missedStarts, Set<Long> completedTaskIds, Map<Long, String> titles) {
	}

	private static Disposition disposeOf(List<TimeBlock> existing, LocalDateTime now) {
		List<TimeBlock> obstacles = new ArrayList<>();
		List<TimeBlock> doneHistory = new ArrayList<>();
		List<TimeBlock> toRemove = new ArrayList<>();
		List<TimeBlock> stillPlanned = new ArrayList<>();
		Map<Long, LocalDateTime> missedStarts = new HashMap<>();
		Set<Long> completed = new LinkedHashSet<>();
		Map<Long, String> titles = new HashMap<>();

		for (TimeBlock block : existing) {
			titles.put(block.getTask().getId(), block.getTask().getTitle());
			boolean ended = block.hasEndedBy(now);
			if (block.getTask().getStatus() == TaskStatus.DONE) {
				completed.add(block.getTask().getId());
			}
			if (block.getState() == BlockState.MISSED) {
				// Already reported, already replanned: only a record now.
				continue;
			}
			if (block.getState() == BlockState.DONE) {
				// Still running, it keeps its time like any block in flight; over, it is history.
				(ended ? doneHistory : obstacles).add(block);
				continue;
			}
			boolean entirelyFuture = block.getStartAt().isAfter(now);
			if (!ended) {
				stillPlanned.add(block);
			}

			if (block.getTask().getStatus() == TaskStatus.DONE) {
				if (entirelyFuture) {
					// Finishing early gives the time back.
					toRemove.add(block);
				}
				else if (!ended) {
					// Started and completed but not yet over: its remaining minutes are
					// still occupied, so it must block capacity or new work lands on top.
					obstacles.add(block);
				}
				// An elapsed block is history: untouched, and irrelevant to future capacity.
				continue;
			}

			if (ended) {
				missedStarts.merge(block.getTask().getId(), block.getStartAt(),
						(existingStart, candidate) -> candidate.isBefore(existingStart) ? candidate : existingStart);
				block.markMissed();
			}
			else if (!entirelyFuture || block.isPinned()) {
				// In flight, or pinned. Either way it keeps its time.
				obstacles.add(block);
			}
			else {
				toRemove.add(block);
			}
		}

		return new Disposition(obstacles, doneHistory, toRemove, stillPlanned, missedStarts, completed, titles);
	}

	private static SchedulableTask toSchedulable(Task task, Map<Long, Long> covered) {
		long alreadyCovered = covered.getOrDefault(task.getId(), 0L);
		// Covered means kept obstacles plus parts marked done. A missed part is not covered: the work
		// did not happen unless the person marks it done, which they can still do afterwards.
		int remaining = (int) Math.max(0, task.getEstimatedMinutes() - alreadyCovered);
		return new SchedulableTask(task.getId(), remaining, task.getDeadline(), task.getPriority(),
				task.getNotBefore());
	}

	private static Map<Long, Long> minutesPerTask(List<TimeBlock> blocks) {
		return blocks.stream()
			.collect(Collectors.groupingBy((block) -> block.getTask().getId(),
					Collectors.summingLong((block) -> block.toSlot().minutes())));
	}

	private static List<TimeSlot> slotsOf(List<TimeBlock> blocks) {
		return blocks.stream().map(TimeBlock::toSlot).toList();
	}

	// --- describing what changed -----------------------------------------------------

	private static Map<Long, List<LocalDateTime>> startsPerTask(List<TimeBlock> blocks) {
		return blocks.stream()
			.collect(Collectors.groupingBy((block) -> block.getTask().getId(),
					Collectors.mapping(TimeBlock::getStartAt, Collectors.toList())))
			.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().stream().sorted().toList()));
	}

	private static Map<Long, List<LocalDateTime>> startsAfter(List<TimeBlock> kept, List<PlannedBlock> planned) {
		Map<Long, List<LocalDateTime>> result = new HashMap<>();
		for (TimeBlock block : kept) {
			result.computeIfAbsent(block.getTask().getId(), (id) -> new ArrayList<>()).add(block.getStartAt());
		}
		for (PlannedBlock block : planned) {
			result.computeIfAbsent(block.taskId(), (id) -> new ArrayList<>()).add(block.start());
		}
		result.values().forEach((starts) -> starts.sort(Comparator.naturalOrder()));
		return result;
	}

	private Optional<RescheduleEvent> record(long userId, RescheduleTrigger trigger, LocalDateTime now,
			Map<Long, List<LocalDateTime>> before, Map<Long, List<LocalDateTime>> after, Disposition disposition,
			Map<Long, Task> byId) {

		Map<Long, LocalDateTime> missedStarts = disposition.missedStarts();
		Set<Long> touched = new LinkedHashSet<>();
		touched.addAll(missedStarts.keySet());
		touched.addAll(before.keySet());
		touched.addAll(after.keySet());
		// Finishing a task hands its future time back. That is the system working, not a
		// scheduling event, and reporting it as UNPLACED would be actively misleading.
		touched.removeAll(disposition.completedTaskIds());

		List<RescheduleEventItem> items = new ArrayList<>();
		for (Long taskId : touched) {
			List<LocalDateTime> was = before.getOrDefault(taskId, List.of());
			List<LocalDateTime> is = after.getOrDefault(taskId, List.of());
			boolean missed = missedStarts.containsKey(taskId);
			RescheduleItemKind kind = classify(missed, was, is);
			if (kind == null) {
				continue;
			}
			items.add(new RescheduleEventItem(taskId, titleOf(taskId, disposition, byId), kind,
					missed ? missedStarts.get(taskId) : first(was), first(is)));
		}

		if (items.isEmpty()) {
			return Optional.empty();
		}

		RescheduleEvent event = new RescheduleEvent(userId, now, trigger, summarize(items));
		items.forEach(event::add);
		return Optional.of(this.events.save(event));
	}

	/**
	 * A task that never had a place and still has none is not reported: it would repeat on
	 * every run. The shortfall between estimated and scheduled minutes already exposes it,
	 * and that value is derived so it cannot go stale.
	 */
	private static RescheduleItemKind classify(boolean missed, List<LocalDateTime> before,
			List<LocalDateTime> after) {
		if (missed) {
			return RescheduleItemKind.MISSED;
		}
		if (!before.isEmpty() && after.isEmpty()) {
			return RescheduleItemKind.UNPLACED;
		}
		if (before.isEmpty() && !after.isEmpty()) {
			return RescheduleItemKind.PLACED;
		}
		return before.equals(after) ? null : RescheduleItemKind.MOVED;
	}

	/**
	 * Titles come from the blocks first, because those cover tasks of any status. The
	 * schedulable map alone would miss a completed task and mislabel it as deleted.
	 */
	private static String titleOf(Long taskId, Disposition disposition, Map<Long, Task> byId) {
		String fromBlocks = disposition.titles().get(taskId);
		if (fromBlocks != null) {
			return fromBlocks;
		}
		Task task = byId.get(taskId);
		return (task != null) ? task.getTitle() : "(deleted task)";
	}

	private static LocalDateTime first(List<LocalDateTime> starts) {
		return starts.isEmpty() ? null : starts.get(0);
	}

	private static String summarize(List<RescheduleEventItem> items) {
		Map<RescheduleItemKind, Long> counts = items.stream()
			.collect(Collectors.groupingBy(RescheduleEventItem::getKind, Collectors.counting()));
		return counts.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map((entry) -> entry.getValue() + " " + entry.getKey().name().toLowerCase())
			.collect(Collectors.joining(", "));
	}

}
