package dev.karimbk.reflowtask.schedule;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import dev.karimbk.reflowtask.SettableClock;
import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.Task;
import dev.karimbk.reflowtask.task.TaskRepository;
import dev.karimbk.reflowtask.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reflow behaviour end to end: real database, real Flyway schema, real planner, with only
 * the clock under test control.
 *
 * These assertions are the product's actual promise — that a missed block does not leave the
 * user to replan by hand — so they go through the service rather than the planner.
 */
@SpringBootTest
@Transactional
class ReflowTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		SettableClock testClock() {
			return new SettableClock(MONDAY.atTime(9, 0), ZoneId.of("Europe/Berlin"));
		}

	}

	@Autowired
	private SchedulerService scheduler;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private TimeBlockRepository blocks;

	@Autowired
	private SettableClock clock;

	@BeforeEach
	void resetTime() {
		this.clock.set(MONDAY.atTime(9, 0));
	}

	private Task givenTask(String title, int minutes, LocalDateTime deadline, Priority priority) {
		return this.tasks.save(new Task(title, null, minutes, deadline, deadline != null, priority,
				MONDAY.atTime(8, 0)));
	}

	private List<TimeBlock> blocksOf(Task task) {
		return this.blocks.findAllWithTask()
			.stream()
			.filter((block) -> block.getTask().getId().equals(task.getId()))
			.sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
			.toList();
	}

	// --- placement ------------------------------------------------------------------

	@Test
	void anOpenTaskGetsScheduledAndTheEventSaysSo() {
		Task task = givenTask("Write docs", 120, null, Priority.MEDIUM);

		Optional<RescheduleEvent> event = this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		assertThat(blocksOf(task)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(9, 0)));
		assertThat(event).isPresent();
		assertThat(event.get().getItems()).singleElement()
			.satisfies((item) -> assertThat(item.getKind()).isEqualTo(RescheduleItemKind.PLACED));
	}

	@Test
	void replanningWithNothingToChangeRecordsNoEvent() {
		givenTask("Write docs", 120, null, Priority.MEDIUM);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		assertThat(this.scheduler.replan(RescheduleTrigger.SCHEDULED_JOB)).isEmpty();
	}

	// --- the differentiator ---------------------------------------------------------

	@Test
	void aMissedBlockIsRemovedAndTheTaskIsReplannedLater() {
		Task task = givenTask("Write docs", 120, null, Priority.MEDIUM);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		assertThat(blocksOf(task).get(0).getStartAt()).isEqualTo(MONDAY.atTime(9, 0));

		// The block ran 09:00-11:00 and was never completed. It is now the afternoon.
		this.clock.set(MONDAY.atTime(14, 0));
		Optional<RescheduleEvent> event = this.scheduler.replan(RescheduleTrigger.SCHEDULED_JOB);

		assertThat(blocksOf(task)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(14, 0)));
		assertThat(event).isPresent();
		RescheduleEventItem item = event.get().getItems().get(0);
		assertThat(item.getKind()).isEqualTo(RescheduleItemKind.MISSED);
		assertThat(item.getPreviousStartAt()).isEqualTo(MONDAY.atTime(9, 0));
		assertThat(item.getNewStartAt()).isEqualTo(MONDAY.atTime(14, 0));
		assertThat(item.getTaskTitle()).isEqualTo("Write docs");
	}

	@Test
	void aCompletedTaskIsNotTreatedAsMissed() {
		Task task = givenTask("Write docs", 120, null, Priority.MEDIUM);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		task.setStatus(TaskStatus.DONE);

		this.clock.set(MONDAY.atTime(14, 0));
		this.scheduler.replan(RescheduleTrigger.SCHEDULED_JOB);

		// The elapsed block stays as the record of when the work was done.
		assertThat(blocksOf(task)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(9, 0)));
	}

	@Test
	void aBlockStillRunningIsNotTouched() {
		Task task = givenTask("Write docs", 120, null, Priority.MEDIUM);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		// 09:30 is inside the 09:00-11:00 block: the user may be working on it right now.
		this.clock.set(MONDAY.atTime(9, 30));
		this.scheduler.replan(RescheduleTrigger.SCHEDULED_JOB);

		assertThat(blocksOf(task)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(9, 0)));
	}

	@Test
	void finishingEarlyFreesTheTimeForOtherWork() {
		// Planned before the working day starts, so every block below is still in the future.
		// (This scenario once leaned on a seeded lunch break splitting the task into a started
		// and an unstarted piece; with no default breaks it has to stand on its own. Keeping
		// work that has already started is covered by aBlockStillRunningIsNotTouched.)
		this.clock.set(MONDAY.atTime(8, 0));
		Task finished = givenTask("Finished early", 240, null, Priority.HIGH);
		Task waiting = givenTask("Waiting", 60, null, Priority.LOW);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		// Four hours of higher-priority work takes 09:00-13:00, so the waiting task follows it.
		assertThat(blocksOf(waiting).get(0).getStartAt()).isEqualTo(MONDAY.atTime(13, 0));

		finished.setStatus(TaskStatus.DONE);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		// None of the finished task's time had started, so all of it is released and the waiting
		// task moves up to the start of the day.
		assertThat(blocksOf(waiting).get(0).getStartAt()).isEqualTo(MONDAY.atTime(9, 0));
	}

	@Test
	void completingATaskIsNotReportedAsLosingItsSlot() {
		// Regression: the completed task used to appear as UNPLACED and, because the lookup
		// only covered schedulable tasks, was even labelled "(deleted task)". Handing time
		// back is the system working; only the knock-on moves belong in the history.
		Task finished = givenTask("Finished", 60, null, Priority.HIGH);
		Task other = givenTask("Other", 60, null, Priority.LOW);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		this.clock.set(MONDAY.atTime(8, 0));
		finished.setStatus(TaskStatus.DONE);
		Optional<RescheduleEvent> event = this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		assertThat(event).isPresent();
		assertThat(event.get().getItems()).noneSatisfy((item) -> {
			assertThat(item.getTaskId()).isEqualTo(finished.getId());
		});
		assertThat(event.get().getItems()).allSatisfy((item) -> {
			assertThat(item.getTaskTitle()).isNotEqualTo("(deleted task)");
			assertThat(item.getTaskId()).isEqualTo(other.getId());
		});
	}

	@Test
	void aDeletedTaskDoesNotBreakTheFollowingReplan() {
		Task doomed = givenTask("Doomed", 60, null, Priority.HIGH);
		givenTask("Survivor", 60, null, Priority.LOW);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		this.scheduler.releaseBlocksOf(doomed.getId());
		this.tasks.delete(doomed);
		this.tasks.flush();

		assertThat(this.scheduler.replan(RescheduleTrigger.TASK_CHANGED)).isPresent();
		assertThat(blocksOf(doomed)).isEmpty();
	}

	// --- pinning --------------------------------------------------------------------

	@Test
	void aPinnedBlockSurvivesAReplanAndPushesOtherWorkAside() {
		Task pinnedTask = givenTask("Dentist", 60, null, Priority.LOW);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		TimeBlock block = blocksOf(pinnedTask).get(0);
		block.setPinned(true);

		Task urgent = givenTask("Urgent", 60, MONDAY.atTime(12, 0), Priority.HIGH);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		// Despite being lower priority and undated, the pinned block keeps 09:00.
		assertThat(blocksOf(pinnedTask)).singleElement()
			.satisfies((kept) -> assertThat(kept.getStartAt()).isEqualTo(MONDAY.atTime(9, 0)));
		assertThat(blocksOf(urgent).get(0).getStartAt()).isEqualTo(MONDAY.atTime(10, 0));
	}

	@Test
	void onlyTheUnpinnedRemainderOfAPartlyPinnedTaskIsReplanned() {
		Task task = givenTask("Long job", 240, null, Priority.MEDIUM);
		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);
		blocksOf(task).forEach((block) -> block.setPinned(true));

		this.scheduler.replan(RescheduleTrigger.SCHEDULED_JOB);

		// Fully covered by pinned blocks, so nothing new is added.
		assertThat(blocksOf(task).stream().mapToLong((block) -> block.toSlot().minutes()).sum()).isEqualTo(240);
	}

	// --- ordering and at-risk -------------------------------------------------------

	@Test
	void theMoreUrgentTaskClaimsTheEarlierSlotAcrossAReplan() {
		givenTask("Later", 60, MONDAY.atTime(17, 0), Priority.MEDIUM);
		Task sooner = givenTask("Sooner", 60, MONDAY.atTime(11, 0), Priority.MEDIUM);

		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		assertThat(blocksOf(sooner).get(0).getStartAt()).isEqualTo(MONDAY.atTime(9, 0));
	}

	@Test
	void workThatCannotMeetItsDeadlineIsStillScheduled() {
		// 480 minutes of high-priority work first, then an hour due before it can happen.
		givenTask("Fills the day", 480, MONDAY.atTime(9, 30), Priority.HIGH);
		Task doomed = givenTask("Impossible", 60, MONDAY.atTime(10, 0), Priority.HIGH);

		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		List<TimeBlock> placed = blocksOf(doomed);
		assertThat(placed).isNotEmpty();
		assertThat(placed.get(0).getEndAt()).isAfter(doomed.getDeadline());
	}

	@Test
	void anExhaustedHorizonLeavesAVisibleShortfallRatherThanFailing() {
		// Far more work than the 14-day horizon can hold.
		Task huge = givenTask("Huge", 480 * 40, null, Priority.MEDIUM);

		this.scheduler.replan(RescheduleTrigger.TASK_CHANGED);

		long scheduled = blocksOf(huge).stream().mapToLong((block) -> block.toSlot().minutes()).sum();
		assertThat(scheduled).isGreaterThan(0).isLessThan(huge.getEstimatedMinutes());
	}

	// --- the clock bean -------------------------------------------------------------

	@Test
	void theApplicationClockIsOverriddenForTheseTests() {
		assertThat(LocalDateTime.now((Clock) this.clock)).isEqualTo(MONDAY.atTime(9, 0));
	}

}
