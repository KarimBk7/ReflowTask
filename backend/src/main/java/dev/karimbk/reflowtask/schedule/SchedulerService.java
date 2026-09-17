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

import dev.karimbk.reflowtask.common.ConflictException;
import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.config.SchedulingConfigProvider;
import dev.karimbk.reflowtask.task.Task;
import dev.karimbk.reflowtask.task.TaskRepository;
import dev.karimbk.reflowtask.task.TaskStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the planner's decisions into persisted blocks, and records what changed.
 *
 * The rules about which existing blocks survive a replan live here, and they are the part
 * users actually feel:
 *
 * <ul>
 * <li>Blocks of completed tasks that have already started are history and never touched.
 * Future blocks of a completed task are removed, so finishing early frees the time.</li>
 * <li>A block the user is currently inside is left alone. Moving the thing someone is
 * working on right now would be hostile, pinned or not.</li>
 * <li>A block whose time has passed while its task is unfinished is a miss: it is removed
 * and the task is replanned.</li>
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

	private final Clock clock;

	SchedulerService(TaskRepository tasks, TimeBlockRepository blocks, RescheduleEventRepository events,
			SchedulingConfigProvider config, Clock clock) {
		this.tasks = tasks;
		this.blocks = blocks;
		this.events = events;
		this.config = config;
		this.clock = clock;
	}

	/**
	 * Refuses to fix work at a time the schedule cannot give it. Called before anything is
	 * written, so a refused request leaves no task behind without its block.
	 *
	 * @param ignoreBlockId the block being moved, which may of course overlap its own old place
	 */
	@Transactional(readOnly = true)
	public void assertCanFix(LocalDateTime start, LocalDateTime end, Long ignoreBlockId) {
		if (!end.isAfter(LocalDateTime.now(this.clock))) {
			throw new ConflictException("Work cannot be fixed at a time that has already passed.");
		}
		boolean clash = this.blocks.findOverlapping(start, end)
			.stream()
			.anyMatch((block) -> block.isPinned() && !block.getId().equals(ignoreBlockId));
		if (clash) {
			throw new ConflictException("That time overlaps something that is already fixed.");
		}
	}

	/** Fixes a task at a chosen time with a pinned block covering its whole estimate. */
	@Transactional
	public void fix(Task task, LocalDateTime start) {
		LocalDateTime end = start.plusMinutes(task.getEstimatedMinutes());
		assertCanFix(start, end, null);
		this.blocks.save(new TimeBlock(task, start, end, true));
	}

	/**
	 * Moves or resizes a block to where the user dragged it, pins it there, and replans the rest.
	 *
	 * Resizing changes the task's estimate by exactly the change in length: stretching a block
	 * says the work takes longer. Moving without resizing leaves the estimate alone.
	 */
	@Transactional
	public TimeBlock move(long blockId, LocalDateTime start, LocalDateTime end) {
		TimeBlock block = this.blocks.findById(blockId).orElseThrow(() -> new NotFoundException("Time block", blockId));
		LocalDateTime now = LocalDateTime.now(this.clock);
		if (block.getTask().getStatus() == TaskStatus.DONE) {
			throw new ConflictException("A completed task's time cannot be moved.");
		}
		if (block.hasEndedBy(now)) {
			throw new ConflictException("Time that has already passed cannot be moved.");
		}
		assertCanFix(start, end, blockId);

		long change = Duration.between(start, end).toMinutes()
				- Duration.between(block.getStartAt(), block.getEndAt()).toMinutes();
		Task task = block.getTask();
		task.setEstimatedMinutes((int) Math.clamp(task.getEstimatedMinutes() + change, 1, MAX_ESTIMATE_MINUTES));

		block.moveTo(start, end);
		this.blocks.flush();
		replan(RescheduleTrigger.MANUAL);
		return block;
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
	 * Rebuilds the schedule. Returns the recorded event, or empty when nothing actually
	 * changed — a replan that moved nothing is not news, and writing it anyway would bury
	 * the real events in noise.
	 */
	@Transactional
	public Optional<RescheduleEvent> replan(RescheduleTrigger trigger) {
		LocalDateTime now = LocalDateTime.now(this.clock);
		Disposition disposition = disposeOf(this.blocks.findAllWithTask(), now);

		Map<Long, Task> byId = this.tasks.findByStatusNot(TaskStatus.DONE)
			.stream()
			.collect(Collectors.toMap(Task::getId, Function.identity()));
		Map<Long, Long> covered = minutesPerTask(disposition.obstacles());

		List<SchedulableTask> toPlan = byId.values()
			.stream()
			.map((task) -> toSchedulable(task, covered))
			.filter((candidate) -> candidate.minutesToPlace() > 0)
			.toList();

		Map<Long, List<LocalDateTime>> before = startsPerTask(disposition.stillPlanned());

		List<PlannedBlock> planned = SchedulePlanner.plan(toPlan, slotsOf(disposition.obstacles()),
				this.config.current(), now);

		this.blocks.deleteAll(disposition.toRemove());
		this.blocks.flush();
		for (PlannedBlock block : planned) {
			Task task = byId.get(block.taskId());
			this.blocks.save(new TimeBlock(task, block.start(), block.end(), false));
		}

		Map<Long, List<LocalDateTime>> after = startsAfter(disposition.obstacles(), planned);
		return record(trigger, now, before, after, disposition, byId);
	}

	// --- deciding what survives ------------------------------------------------------

	/**
	 * @param obstacles blocks that keep their time and consume capacity
	 * @param toRemove blocks being deleted, whether missed, superseded or freed
	 * @param stillPlanned blocks that counted as "the plan" before this run, so a move can
	 * be detected
	 * @param missedStarts earliest missed start per task
	 * @param completedTaskIds tasks finished in the meantime. Releasing their future time is
	 * not a scheduling event, so they are left out of the history entirely.
	 * @param titles every task seen through a block, so the history can name a task whatever
	 * its status
	 */
	private record Disposition(List<TimeBlock> obstacles, List<TimeBlock> toRemove, List<TimeBlock> stillPlanned,
			Map<Long, LocalDateTime> missedStarts, Set<Long> completedTaskIds, Map<Long, String> titles) {
	}

	private static Disposition disposeOf(List<TimeBlock> existing, LocalDateTime now) {
		List<TimeBlock> obstacles = new ArrayList<>();
		List<TimeBlock> toRemove = new ArrayList<>();
		List<TimeBlock> stillPlanned = new ArrayList<>();
		Map<Long, LocalDateTime> missedStarts = new HashMap<>();
		Set<Long> completed = new LinkedHashSet<>();
		Map<Long, String> titles = new HashMap<>();

		for (TimeBlock block : existing) {
			titles.put(block.getTask().getId(), block.getTask().getTitle());
			boolean ended = block.hasEndedBy(now);
			boolean entirelyFuture = block.getStartAt().isAfter(now);
			if (!ended) {
				stillPlanned.add(block);
			}

			if (block.getTask().getStatus() == TaskStatus.DONE) {
				completed.add(block.getTask().getId());
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
				toRemove.add(block);
			}
			else if (!entirelyFuture || block.isPinned()) {
				// In flight, or pinned. Either way it keeps its time.
				obstacles.add(block);
			}
			else {
				toRemove.add(block);
			}
		}

		return new Disposition(obstacles, toRemove, stillPlanned, missedStarts, completed, titles);
	}

	private static SchedulableTask toSchedulable(Task task, Map<Long, Long> covered) {
		long alreadyCovered = covered.getOrDefault(task.getId(), 0L);
		// Minutes from blocks that already elapsed are NOT treated as covered: without time
		// tracking there is no way to know whether the work actually happened, and the spec
		// says an unfinished block counts as missed.
		// ponytail: whole estimate is replanned on a miss; refine once actuals are tracked.
		int remaining = (int) Math.max(0, task.getEstimatedMinutes() - alreadyCovered);
		return new SchedulableTask(task.getId(), remaining, task.getDeadline(), task.getPriority());
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

	private Optional<RescheduleEvent> record(RescheduleTrigger trigger, LocalDateTime now,
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

		RescheduleEvent event = new RescheduleEvent(now, trigger, summarize(items));
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
