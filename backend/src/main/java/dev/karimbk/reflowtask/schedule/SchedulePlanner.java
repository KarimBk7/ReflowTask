package dev.karimbk.reflowtask.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.TimeProfile;

/**
 * The scheduling core, deliberately a pure function: given tasks, obstacles, configuration
 * and the current time, it returns where work should go. No database, no injected clock, no
 * side effects — so every rule below is provable by a unit test.
 *
 * The algorithm is a greedy pass, chosen on purpose for the MVP: sort by urgency, then fill
 * the earliest free capacity. It does not attempt to optimize, and it does not pretend to.
 */
public final class SchedulePlanner {

	/**
	 * Free capacity starts on a clean quarter-hour. Without this, replanning at 10:37 would
	 * produce a calendar full of 10:37 starts — correct, but it reads as broken. Raise or
	 * lower if the week view ever uses a different grid.
	 */
	private static final int SLOT_GRANULARITY_MINUTES = 15;

	private SchedulePlanner() {
	}

	/**
	 * @param tasks everything still needing time, in any order
	 * @param obstacles time already committed and immovable — pinned blocks
	 * @param now the moment planning happens; nothing is placed before it
	 */
	public static List<PlannedBlock> plan(List<SchedulableTask> tasks, List<TimeSlot> obstacles,
			SchedulingConfig config, LocalDateTime now) {
		// One free list per profile, because each has its own hours. A person still does one thing at
		// a time, so whatever one profile takes is cut from the others as well.
		Map<TimeProfile, List<TimeSlot>> free = new EnumMap<>(TimeProfile.class);
		for (TimeProfile profile : TimeProfile.values()) {
			free.put(profile, freeCapacity(obstacles, config, now, profile));
		}
		Map<Long, SchedulableTask> byId = tasks.stream()
			.collect(Collectors.toMap(SchedulableTask::id, Function.identity()));
		Set<Long> handled = new HashSet<>();
		List<PlannedBlock> planned = new ArrayList<>();
		for (SchedulableTask task : inPlanningOrder(tasks)) {
			placeAfterItsPredecessor(task, byId, handled, free, config, planned);
		}
		return planned;
	}

	/**
	 * Places a task once whatever it waits for is placed. A task that waits for a less urgent one
	 * pulls that one forward rather than being pushed back behind it; if the one it waits for finds
	 * no time, neither does it, since it could not start anyway.
	 */
	private static void placeAfterItsPredecessor(SchedulableTask task, Map<Long, SchedulableTask> byId,
			Set<Long> handled, Map<TimeProfile, List<TimeSlot>> free, SchedulingConfig config,
			List<PlannedBlock> planned) {
		// Marked before recursing, so a cycle ends instead of looping; the API refuses cycles anyway.
		if (!handled.add(task.id())) {
			return;
		}
		LocalDateTime notBefore = task.notBefore();
		SchedulableTask first = (task.after() == null) ? null : byId.get(task.after());
		if (first != null) {
			placeAfterItsPredecessor(first, byId, handled, free, config, planned);
			List<PlannedBlock> itsBlocks = planned.stream().filter((block) -> block.taskId() == first.id()).toList();
			long placedMinutes = itsBlocks.stream().mapToLong(PlannedBlock::minutes).sum();
			LocalDateTime itsEnd = (placedMinutes < first.minutesToPlace()) ? LocalDateTime.MAX
					: itsBlocks.stream().map(PlannedBlock::end).max(Comparator.naturalOrder()).orElseThrow();
			notBefore = (notBefore == null || itsEnd.isAfter(notBefore)) ? itsEnd : notBefore;
		}
		SchedulableTask waiting = new SchedulableTask(task.id(), task.minutesToPlace(), task.deadline(),
				task.priority(), notBefore, task.profile(), task.after());

		List<PlannedBlock> placed = new ArrayList<>();
		place(waiting, free.get(task.profile()), config.minChunkMinutes(), config.bufferMinutes(), placed);
		for (TimeProfile other : TimeProfile.values()) {
			if (other != task.profile()) {
				for (PlannedBlock block : placed) {
					free.put(other, subtract(free.get(other),
							new TimeSlot(block.start(), block.end().plusMinutes(config.bufferMinutes()))));
				}
			}
		}
		planned.addAll(placed);
	}

	/**
	 * Earliest deadline first, then highest priority, then oldest id.
	 *
	 * Tasks without a deadline sort last so dated work claims the early slots. The id
	 * tie-break exists so the same inputs always produce the same plan — a scheduler that
	 * shuffles equivalent tasks between runs would make every replan look like a change.
	 */
	static List<SchedulableTask> inPlanningOrder(List<SchedulableTask> tasks) {
		Comparator<SchedulableTask> order = Comparator
			.comparing(SchedulableTask::deadline, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(SchedulableTask::priority, Comparator.<Priority>reverseOrder())
			.thenComparingLong(SchedulableTask::id);
		return tasks.stream().sorted(order).toList();
	}

	/**
	 * Working hours across the horizon, minus blocked periods, minus the obstacles, minus
	 * everything already in the past.
	 */
	static List<TimeSlot> freeCapacity(List<TimeSlot> obstacles, SchedulingConfig config, LocalDateTime now) {
		return freeCapacity(obstacles, config, now, TimeProfile.WORK);
	}

	static List<TimeSlot> freeCapacity(List<TimeSlot> obstacles, SchedulingConfig config, LocalDateTime now,
			TimeProfile profile) {
		LocalDateTime earliest = ceilingTo(now, SLOT_GRANULARITY_MINUTES);
		LocalDate firstDay = earliest.toLocalDate();
		List<TimeSlot> free = new ArrayList<>();

		for (int dayOffset = 0; dayOffset < config.horizonDays(); dayOffset++) {
			LocalDate date = firstDay.plusDays(dayOffset);
			for (DailyWindow window : config.hoursOn(profile, date.getDayOfWeek())) {
				TimeSlot remaining = window.on(date).notBefore(earliest);
				if (remaining == null) {
					continue;
				}
				List<TimeSlot> pieces = new ArrayList<>(List.of(remaining));
				for (DailyWindow blocked : config.blockedPeriodsOn(date.getDayOfWeek())) {
					pieces = subtract(pieces, blocked.on(date));
				}
				for (TimeSlot obstacle : obstacles) {
					// Widened by the buffer on both sides, so a fixed appointment keeps room
					// to arrive at and recover from. With no buffer this is the obstacle itself.
					pieces = subtract(pieces, new TimeSlot(obstacle.start().minusMinutes(config.bufferMinutes()),
							obstacle.end().plusMinutes(config.bufferMinutes())));
				}
				free.addAll(pieces);
			}
		}

		free.sort(Comparator.comparing(TimeSlot::start));
		return free;
	}

	/** Removes {@code cut} from every slot it overlaps, splitting slots where it lands inside one. */
	static List<TimeSlot> subtract(List<TimeSlot> slots, TimeSlot cut) {
		List<TimeSlot> result = new ArrayList<>(slots.size() + 1);
		for (TimeSlot slot : slots) {
			if (!slot.overlaps(cut)) {
				result.add(slot);
				continue;
			}
			if (slot.start().isBefore(cut.start())) {
				result.add(new TimeSlot(slot.start(), cut.start()));
			}
			if (cut.end().isBefore(slot.end())) {
				result.add(new TimeSlot(cut.end(), slot.end()));
			}
		}
		return result;
	}

	/**
	 * Places the task in one block when that costs nothing, and splits it only when it must.
	 *
	 * Splitting is worked out first, on a copy of the free capacity. If it produces more than one
	 * piece, the task is kept whole instead in the earliest gap that holds all of it, provided that
	 * gap starts no later than splitting would have started its last piece, and does not miss a
	 * deadline splitting would meet. So a one-hour task never shows up as two half-hours around
	 * someone's meeting when an hour-long gap follows anyway, while a six-hour task still splits
	 * at lunch, and nothing is ever pushed later than the greedy pass would have put it.
	 */
	private static void place(SchedulableTask task, List<TimeSlot> free, int minChunkMinutes, int bufferMinutes,
			List<PlannedBlock> planned) {
		long remaining = task.minutesToPlace();
		int first = firstUsable(free, task.notBefore());
		List<PlannedBlock> split = fill(task.id(), remaining, new ArrayList<>(free), first, minChunkMinutes,
				bufferMinutes);

		if (split.size() > 1) {
			PlannedBlock last = split.get(split.size() - 1);
			boolean splitMeetsDeadline = task.deadline() == null || !last.end().isAfter(task.deadline());
			for (int index = first; index < free.size(); index++) {
				TimeSlot slot = free.get(index);
				if (slot.start().isAfter(last.start())) {
					break;
				}
				LocalDateTime end = slot.start().plusMinutes(remaining);
				boolean wholeMeetsDeadline = task.deadline() == null || !end.isAfter(task.deadline());
				if (slot.minutes() >= remaining && (wholeMeetsDeadline || !splitMeetsDeadline)) {
					planned.add(new PlannedBlock(task.id(), slot.start(), end));
					consume(free, index, remaining, bufferMinutes);
					return;
				}
			}
		}

		planned.addAll(fill(task.id(), remaining, free, first, minChunkMinutes, bufferMinutes));
	}

	/**
	 * The index of the first free slot a task may use when it must not start before
	 * {@code notBefore}. A slot straddling that moment is cut in two there, so the part after it
	 * is usable on its own.
	 */
	// ponytail: the cut stays for the tasks planned after this one, so a later task could split at
	// that seam. Occurrences start where the previous one was due, usually 23:59, outside working
	// hours, so the cut practically never lands inside a slot.
	static int firstUsable(List<TimeSlot> free, LocalDateTime notBefore) {
		if (notBefore == null) {
			return 0;
		}
		for (int index = 0; index < free.size(); index++) {
			TimeSlot slot = free.get(index);
			if (!slot.end().isAfter(notBefore)) {
				continue;
			}
			if (slot.start().isBefore(notBefore)) {
				free.set(index, new TimeSlot(slot.start(), notBefore));
				free.add(index + 1, new TimeSlot(notBefore, slot.end()));
				return index + 1;
			}
			return index;
		}
		return free.size();
	}

	/**
	 * The greedy pass: fills minutes from the earliest free capacity, consuming what it takes.
	 * Stops when the minutes are placed or the horizon runs out; a shortfall is reported by the
	 * caller comparing placed minutes against the estimate, not by failing.
	 */
	private static List<PlannedBlock> fill(long taskId, long minutes, List<TimeSlot> free, int firstIndex,
			int minChunkMinutes, int bufferMinutes) {
		List<PlannedBlock> pieces = new ArrayList<>();
		long remaining = minutes;
		int index = firstIndex;

		while (remaining > 0 && index < free.size()) {
			TimeSlot slot = free.get(index);
			long chunk = Math.min(slot.minutes(), remaining);

			// Skip a slot too small to be worth splitting into — unless this is the last
			// scrap of the task, where a short final piece is exactly right.
			if (chunk <= 0 || (chunk < minChunkMinutes && remaining >= minChunkMinutes)) {
				index++;
				continue;
			}

			pieces.add(new PlannedBlock(taskId, slot.start(), slot.start().plusMinutes(chunk)));
			remaining -= chunk;
			if (!consume(free, index, chunk, bufferMinutes)) {
				index++;
			}
		}
		return pieces;
	}

	/**
	 * Takes {@code minutes} from the start of a free slot. Whatever work comes next in the slot
	 * starts after the buffer. A task only takes part of a slot when it finishes inside it, so this
	 * never wedges a buffer between two pieces of the same task: those always land in separate slots.
	 *
	 * @return true when the slot was used up and removed, so the next slot now sits at this index
	 */
	private static boolean consume(List<TimeSlot> free, int index, long minutes, int bufferMinutes) {
		TimeSlot slot = free.get(index);
		long consumed = minutes + bufferMinutes;
		if (consumed >= slot.minutes()) {
			free.remove(index);
			return true;
		}
		free.set(index, slot.startingAt(slot.start().plusMinutes(consumed)));
		return false;
	}

	/** Rounds up to the next {@code step}-minute boundary, leaving exact boundaries alone. */
	static LocalDateTime ceilingTo(LocalDateTime time, int step) {
		LocalDateTime floor = time.truncatedTo(ChronoUnit.MINUTES).withMinute(time.getMinute() / step * step);
		return floor.isBefore(time) ? floor.plusMinutes(step) : floor;
	}

}
