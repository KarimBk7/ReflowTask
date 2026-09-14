package dev.karimbk.reflowtask.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import dev.karimbk.reflowtask.task.Priority;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scheduler's contract. Every rule the greedy pass promises is pinned here, because the
 * planner is a pure function and therefore has no excuse for being hard to test.
 *
 * Dates are derived rather than written literally, so no test depends on the author having
 * counted weekdays correctly.
 */
class SchedulePlannerTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	private static final LocalTime NINE = LocalTime.of(9, 0);

	private static final LocalTime SIX_PM = LocalTime.of(18, 0);

	/** Mon-Fri 09:00-18:00 with a 12:00-13:00 lunch: 480 usable minutes per working day. */
	private static SchedulingConfig defaultConfig() {
		return configWithHorizon(14);
	}

	private static SchedulingConfig configWithHorizon(int horizonDays) {
		List<DailyWindow> working = List.of(new DailyWindow(DayOfWeek.MONDAY, NINE, SIX_PM),
				new DailyWindow(DayOfWeek.TUESDAY, NINE, SIX_PM), new DailyWindow(DayOfWeek.WEDNESDAY, NINE, SIX_PM),
				new DailyWindow(DayOfWeek.THURSDAY, NINE, SIX_PM), new DailyWindow(DayOfWeek.FRIDAY, NINE, SIX_PM));
		List<DailyWindow> lunch = working.stream()
			.map((day) -> new DailyWindow(day.day(), LocalTime.NOON, LocalTime.of(13, 0)))
			.toList();
		return new SchedulingConfig(working, lunch, horizonDays, 30);
	}

	private static SchedulableTask task(long id, int minutes, LocalDateTime deadline, Priority priority) {
		return new SchedulableTask(id, minutes, deadline, priority);
	}

	private static LocalDateTime at(LocalDate date, int hour, int minute) {
		return date.atTime(hour, minute);
	}

	@Test
	void theTestFixtureUsesRealWeekdays() {
		assertThat(MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
	}

	// --- placement basics -----------------------------------------------------------

	@Test
	void placesWorkInTheEarliestFreeSlot() {
		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 120, null, Priority.MEDIUM)), List.of(),
				defaultConfig(), at(MONDAY, 9, 0));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY, 9, 0), at(MONDAY, 11, 0)));
	}

	@Test
	void roundsTheFirstStartUpToAQuarterHour() {
		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 60, null, Priority.MEDIUM)), List.of(),
				defaultConfig(), at(MONDAY, 10, 37));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY, 10, 45), at(MONDAY, 11, 45)));
	}

	@Test
	void placesNothingWhenThereIsNothingToPlace() {
		assertThat(SchedulePlanner.plan(List.of(), List.of(), defaultConfig(), at(MONDAY, 9, 0))).isEmpty();
	}

	// --- ordering -------------------------------------------------------------------

	@Test
	void theEarlierDeadlineTakesTheEarlierSlot() {
		SchedulableTask later = task(1, 60, at(MONDAY, 17, 0), Priority.MEDIUM);
		SchedulableTask sooner = task(2, 60, at(MONDAY, 11, 0), Priority.MEDIUM);

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(later, sooner), List.of(), defaultConfig(),
				at(MONDAY, 9, 0));

		assertThat(plan.get(0).taskId()).isEqualTo(2);
		assertThat(plan.get(1).taskId()).isEqualTo(1);
	}

	@Test
	void priorityBreaksADeadlineTie() {
		LocalDateTime sameDeadline = at(MONDAY, 17, 0);
		SchedulableTask low = task(1, 60, sameDeadline, Priority.LOW);
		SchedulableTask high = task(2, 60, sameDeadline, Priority.HIGH);

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(low, high), List.of(), defaultConfig(),
				at(MONDAY, 9, 0));

		assertThat(plan.get(0).taskId()).isEqualTo(2);
	}

	@Test
	void aTaskWithoutADeadlineIsPlannedAfterDatedWork() {
		SchedulableTask undated = task(1, 60, null, Priority.HIGH);
		SchedulableTask dated = task(2, 60, at(MONDAY, 17, 0), Priority.LOW);

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(undated, dated), List.of(), defaultConfig(),
				at(MONDAY, 9, 0));

		assertThat(plan.get(0).taskId()).isEqualTo(2);
		assertThat(plan.get(1).taskId()).isEqualTo(1);
	}

	@Test
	void identicalTasksAlwaysPlanInTheSameOrder() {
		SchedulableTask first = task(7, 60, null, Priority.MEDIUM);
		SchedulableTask second = task(9, 60, null, Priority.MEDIUM);

		List<PlannedBlock> forwards = SchedulePlanner.plan(List.of(first, second), List.of(), defaultConfig(),
				at(MONDAY, 9, 0));
		List<PlannedBlock> backwards = SchedulePlanner.plan(List.of(second, first), List.of(), defaultConfig(),
				at(MONDAY, 9, 0));

		assertThat(forwards).isEqualTo(backwards);
		assertThat(forwards.get(0).taskId()).isEqualTo(7);
	}

	// --- splitting ------------------------------------------------------------------

	@Test
	void splitsWorkTooLongForOneDayAcrossDays() {
		// 600 minutes against 480 per day: Monday fills, Tuesday takes the rest.
		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 600, null, Priority.MEDIUM)), List.of(),
				defaultConfig(), at(MONDAY, 9, 0));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY, 9, 0), at(MONDAY, 12, 0)),
				new PlannedBlock(1, at(MONDAY, 13, 0), at(MONDAY, 18, 0)),
				new PlannedBlock(1, at(MONDAY.plusDays(1), 9, 0), at(MONDAY.plusDays(1), 11, 0)));
		assertThat(plan.stream().mapToLong(PlannedBlock::minutes).sum()).isEqualTo(600);
	}

	@Test
	void onlyTheUnpinnedRemainderIsPlanned() {
		// 300 minutes of work, 120 already covered by a pinned block the caller excluded.
		List<TimeSlot> pinned = List.of(new TimeSlot(at(MONDAY, 9, 0), at(MONDAY, 11, 0)));

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 180, null, Priority.MEDIUM)), pinned,
				defaultConfig(), at(MONDAY, 9, 0));

		assertThat(plan.stream().mapToLong(PlannedBlock::minutes).sum()).isEqualTo(180);
		assertThat(plan.get(0).start()).isEqualTo(at(MONDAY, 11, 0));
	}

	// --- obstacles ------------------------------------------------------------------

	@Test
	void neverSchedulesOverLunch() {
		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 240, null, Priority.MEDIUM)), List.of(),
				defaultConfig(), at(MONDAY, 9, 0));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY, 9, 0), at(MONDAY, 12, 0)),
				new PlannedBlock(1, at(MONDAY, 13, 0), at(MONDAY, 14, 0)));
	}

	@Test
	void neverOverlapsAPinnedBlock() {
		List<TimeSlot> pinned = List.of(new TimeSlot(at(MONDAY, 10, 0), at(MONDAY, 11, 0)));

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 120, null, Priority.MEDIUM)), pinned,
				defaultConfig(), at(MONDAY, 9, 0));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY, 9, 0), at(MONDAY, 10, 0)),
				new PlannedBlock(1, at(MONDAY, 11, 0), at(MONDAY, 12, 0)));
	}

	@Test
	void neverPlacesAnythingOutsideWorkingHoursOrOnAWeekend() {
		// Three full days of work, so the plan has to cross a weekend to fit.
		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 480 * 7, null, Priority.MEDIUM)), List.of(),
				defaultConfig(), at(MONDAY, 9, 0));

		assertThat(plan).isNotEmpty();
		assertThat(plan).allSatisfy((block) -> {
			assertThat(block.start().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
			assertThat(block.start().toLocalTime()).isAfterOrEqualTo(NINE);
			assertThat(block.end().toLocalTime()).isBeforeOrEqualTo(SIX_PM);
			assertThat(block.start().toLocalDate()).isEqualTo(block.end().toLocalDate());
			// No block may straddle lunch.
			boolean straddlesLunch = block.start().toLocalTime().isBefore(LocalTime.NOON)
					&& block.end().toLocalTime().isAfter(LocalTime.NOON);
			assertThat(straddlesLunch).isFalse();
		});
	}

	@Test
	void skipsWeekendsEntirely() {
		LocalDate saturday = MONDAY.plusDays(5);
		assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 60, null, Priority.MEDIUM)), List.of(),
				configWithHorizon(4), at(saturday, 9, 0));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY.plusDays(7), 9, 0), at(MONDAY.plusDays(7), 10, 0)));
	}

	// --- chunking -------------------------------------------------------------------

	@Test
	void skipsASlotTooSmallToBeWorthSplittingInto() {
		// Only 20 free minutes on Monday; minChunk is 30, so the task waits for Tuesday
		// rather than being chipped into a useless fragment.
		List<TimeSlot> pinned = List.of(new TimeSlot(at(MONDAY, 9, 20), at(MONDAY, 18, 0)));

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 60, null, Priority.MEDIUM)), pinned,
				defaultConfig(), at(MONDAY, 9, 0));

		assertThat(plan).containsExactly(
				new PlannedBlock(1, at(MONDAY.plusDays(1), 9, 0), at(MONDAY.plusDays(1), 10, 0)));
	}

	@Test
	void aShortFinalRemainderMayUseASmallSlot() {
		// The same 20-minute slot is fine when the task only needs 20 minutes.
		List<TimeSlot> pinned = List.of(new TimeSlot(at(MONDAY, 9, 20), at(MONDAY, 18, 0)));

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 20, null, Priority.MEDIUM)), pinned,
				defaultConfig(), at(MONDAY, 9, 0));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY, 9, 0), at(MONDAY, 9, 20)));
	}

	// --- deadlines and capacity -----------------------------------------------------

	@Test
	void aTaskWhoseDeadlineHasPassedIsStillPlaced() {
		List<PlannedBlock> plan = SchedulePlanner.plan(
				List.of(task(1, 60, at(MONDAY.minusDays(3), 12, 0), Priority.MEDIUM)), List.of(), defaultConfig(),
				at(MONDAY, 9, 0));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY, 9, 0), at(MONDAY, 10, 0)));
	}

	@Test
	void anExhaustedHorizonPlacesWhatFitsAndLeavesAShortfall() {
		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 600, null, Priority.MEDIUM)), List.of(),
				configWithHorizon(1), at(MONDAY, 9, 0));

		assertThat(plan.stream().mapToLong(PlannedBlock::minutes).sum()).isEqualTo(480);
	}

	@Test
	void noWorkingHoursMeansNothingIsPlacedRatherThanAFailure() {
		SchedulingConfig noCapacity = new SchedulingConfig(List.of(), List.of(), 14, 30);

		assertThat(SchedulePlanner.plan(List.of(task(1, 60, null, Priority.MEDIUM)), List.of(), noCapacity,
				at(MONDAY, 9, 0)))
			.isEmpty();
	}

	@Test
	void capacityAlreadyPastIsNotOffered() {
		// Planning at 17:30 leaves 30 minutes today, then the rest waits for tomorrow.
		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 90, null, Priority.MEDIUM)), List.of(),
				defaultConfig(), at(MONDAY, 17, 30));

		assertThat(plan).containsExactly(new PlannedBlock(1, at(MONDAY, 17, 30), at(MONDAY, 18, 0)),
				new PlannedBlock(1, at(MONDAY.plusDays(1), 9, 0), at(MONDAY.plusDays(1), 10, 0)));
	}

	// --- wall-clock semantics -------------------------------------------------------

	@Test
	void aDaylightSavingChangeDoesNotShiftWorkingHours() {
		// Central European DST begins on this Sunday. Because the whole planner works in
		// wall-clock terms, the Monday after still starts at 09:00 and still offers 480
		// minutes - which is the intended semantic for a personal scheduler.
		LocalDate dstSunday = LocalDate.of(2026, 3, 29);
		assertThat(dstSunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
		LocalDate mondayAfter = dstSunday.plusDays(1);

		List<PlannedBlock> plan = SchedulePlanner.plan(List.of(task(1, 480, null, Priority.MEDIUM)), List.of(),
				configWithHorizon(2), at(dstSunday, 12, 0));

		assertThat(plan.get(0).start()).isEqualTo(at(mondayAfter, 9, 0));
		assertThat(plan.stream().mapToLong(PlannedBlock::minutes).sum()).isEqualTo(480);
	}

}
