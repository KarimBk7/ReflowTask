package dev.karimbk.reflowtask.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

import dev.karimbk.reflowtask.task.Priority;

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
		List<TimeSlot> free = freeCapacity(obstacles, config, now);
		List<PlannedBlock> planned = new ArrayList<>();
		for (SchedulableTask task : inPlanningOrder(tasks)) {
			place(task, free, config.minChunkMinutes(), config.bufferMinutes(), planned);
		}
		return planned;
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
		LocalDateTime earliest = ceilingTo(now, SLOT_GRANULARITY_MINUTES);
		LocalDate firstDay = earliest.toLocalDate();
		List<TimeSlot> free = new ArrayList<>();

		for (int dayOffset = 0; dayOffset < config.horizonDays(); dayOffset++) {
			LocalDate date = firstDay.plusDays(dayOffset);
			for (DailyWindow window : config.workingHoursOn(date.getDayOfWeek())) {
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
	 * Fills the task's remaining minutes from the earliest free capacity, consuming what it
	 * takes. Stops when the task is fully placed or the horizon runs out; a shortfall is
	 * reported by the caller comparing placed minutes against the estimate, not by failing.
	 */
	private static void place(SchedulableTask task, List<TimeSlot> free, int minChunkMinutes, int bufferMinutes,
			List<PlannedBlock> planned) {
		long remaining = task.minutesToPlace();
		ListIterator<TimeSlot> slots = free.listIterator();

		while (remaining > 0 && slots.hasNext()) {
			TimeSlot slot = slots.next();
			long available = slot.minutes();
			if (available <= 0) {
				continue;
			}
			long chunk = Math.min(available, remaining);

			// Skip a slot too small to be worth splitting into — unless this is the last
			// scrap of the task, where a short final piece is exactly right.
			if (chunk < minChunkMinutes && remaining >= minChunkMinutes) {
				continue;
			}

			planned.add(new PlannedBlock(task.id(), slot.start(), slot.start().plusMinutes(chunk)));
			remaining -= chunk;

			// Whatever work comes next in this slot starts after the buffer. A task only takes
			// part of a slot when it finishes inside it, so this never wedges a buffer between
			// two pieces of the same task: those always land in separate slots.
			long consumed = chunk + bufferMinutes;
			if (consumed >= available) {
				slots.remove();
			}
			else {
				slots.set(slot.startingAt(slot.start().plusMinutes(consumed)));
			}
		}
	}

	/** Rounds up to the next {@code step}-minute boundary, leaving exact boundaries alone. */
	static LocalDateTime ceilingTo(LocalDateTime time, int step) {
		LocalDateTime floor = time.truncatedTo(ChronoUnit.MINUTES).withMinute(time.getMinute() / step * step);
		return floor.isBefore(time) ? floor.plusMinutes(step) : floor;
	}

}
