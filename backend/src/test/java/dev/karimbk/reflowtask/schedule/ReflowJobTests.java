package dev.karimbk.reflowtask.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import dev.karimbk.reflowtask.SettableClock;
import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.Task;
import dev.karimbk.reflowtask.task.TaskRepository;
import dev.karimbk.reflowtask.user.Role;
import dev.karimbk.reflowtask.user.User;
import dev.karimbk.reflowtask.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * The hourly job is what makes the schedule repair itself while nobody is looking, so with several
 * people on one server it has to reflow every one of them, and one person's failure must never
 * leave the rest unplanned.
 */
@SpringBootTest
@Transactional
class ReflowJobTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		SettableClock testClock() {
			return new SettableClock(MONDAY.atTime(14, 0), ZoneId.of("Europe/Berlin"));
		}

	}

	@Autowired
	private ReflowJob job;

	@MockitoSpyBean
	private SchedulerService scheduler;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private TimeBlockRepository blocks;

	@Autowired
	private RescheduleEventRepository events;

	@Autowired
	private UserRepository users;

	@Autowired
	private SettableClock clock;

	private long first;

	private long second;

	@BeforeEach
	void twoUsersWithAMissedBlockEach() {
		this.clock.set(MONDAY.atTime(14, 0));
		this.first = this.users.save(new User("job-one", "x", Role.MEMBER, false, LocalDateTime.now())).getId();
		this.second = this.users.save(new User("job-two", "x", Role.MEMBER, false, LocalDateTime.now())).getId();
		for (long user : new long[] { this.first, this.second }) {
			Task task = this.tasks
				.save(new Task(user, "Missed by " + user, null, 60, null, false, Priority.MEDIUM, MONDAY.atTime(8, 0)));
			this.blocks.save(new TimeBlock(task, MONDAY.atTime(9, 0), MONDAY.atTime(10, 0), false));
		}
	}

	private long missedCount(long user) {
		return this.events.findByUserIdOrderByOccurredAtDesc(user, Limit.of(50))
			.stream()
			.flatMap((event) -> event.getItems().stream())
			.filter((item) -> item.getKind() == RescheduleItemKind.MISSED)
			.count();
	}

	@Test
	void everyUsersMissedBlockIsFoundInOneTick() {
		this.job.reflow();

		assertThat(missedCount(this.first)).isEqualTo(1);
		assertThat(missedCount(this.second)).isEqualTo(1);
	}

	@Test
	void eachEventBelongsToTheUserWhoseBlockWasMissed() {
		this.job.reflow();

		assertThat(this.events.findByUserIdOrderByOccurredAtDesc(this.first, Limit.of(50)))
			.allSatisfy((event) -> assertThat(event.getUserId()).isEqualTo(this.first));
		assertThat(this.events.findByUserIdOrderByOccurredAtDesc(this.second, Limit.of(50)))
			.allSatisfy((event) -> assertThat(event.getUserId()).isEqualTo(this.second));
	}

	@Test
	void oneUsersFailureDoesNotStopTheOthersFromBeingReflowed() {
		doThrow(new IllegalStateException("simulated failure")).when(this.scheduler)
			.replan(eq(this.first), eq(RescheduleTrigger.SCHEDULED_JOB));

		this.job.reflow();

		assertThat(missedCount(this.first)).isZero();
		assertThat(missedCount(this.second)).isEqualTo(1);
	}

	@Test
	void aSecondTickWithNothingNewRecordsNothingMore() {
		this.job.reflow();
		long afterFirst = this.events.count();

		this.job.reflow();

		assertThat(this.events.count()).isEqualTo(afterFirst);
	}

}
