package dev.karimbk.reflowtask.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import dev.karimbk.reflowtask.SettableClock;
import dev.karimbk.reflowtask.config.SchedulingSettings;
import dev.karimbk.reflowtask.config.SchedulingSettingsRepository;
import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.Task;
import dev.karimbk.reflowtask.task.TaskRepository;
import dev.karimbk.reflowtask.user.AuthTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The freeze window: what is about to start stays put when something urgent comes in, instead of
 * the next hours being reshuffled under the person who is about to begin them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FreezeWindowTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	private static final long USER_ID = 1L;

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		SettableClock testClock() {
			return new SettableClock(MONDAY.atTime(8, 0), ZoneId.of("Europe/Berlin"));
		}

	}

	@Autowired
	private SchedulerService scheduler;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private TimeBlockRepository blocks;

	@Autowired
	private SchedulingSettingsRepository settings;

	@Autowired
	private SettableClock clock;

	@Autowired
	private MockMvc mvc;

	@BeforeEach
	void beforeTheWorkingDay() {
		this.clock.set(MONDAY.atTime(8, 30));
	}

	private void freezeFor(int minutes) {
		SchedulingSettings current = this.settings.findOrDefault(USER_ID);
		current.setFreezeMinutes(minutes);
		this.settings.save(current);
	}

	private Task givenTask(String title, int minutes, Priority priority, int deadlineHour) {
		return this.tasks.save(new Task(USER_ID, title, null, minutes, MONDAY.atTime(deadlineHour, 0), true, priority,
				MONDAY.atTime(7, 0)));
	}

	private List<TimeBlock> blocksOf(Task task) {
		return this.blocks.findByTaskId(task.getId())
			.stream()
			.sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
			.toList();
	}

	@Test
	void anUrgentTaskDoesNotPushAsideWhatIsAboutToStart() {
		freezeFor(120);
		Task report = givenTask("Report", 60, Priority.MEDIUM, 17);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		assertThat(blocksOf(report).get(0).getStartAt()).isEqualTo(MONDAY.atTime(9, 0));

		Task urgent = givenTask("Urgent", 60, Priority.HIGH, 10);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);

		assertThat(blocksOf(report).get(0).getStartAt()).isEqualTo(MONDAY.atTime(9, 0));
		assertThat(blocksOf(urgent).get(0).getStartAt()).isEqualTo(MONDAY.atTime(10, 0));
	}

	@Test
	void withoutAFreezeTheUrgentTaskTakesTheSlot() {
		Task report = givenTask("Report", 60, Priority.MEDIUM, 17);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);

		Task urgent = givenTask("Urgent", 60, Priority.HIGH, 10);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);

		assertThat(blocksOf(urgent).get(0).getStartAt()).isEqualTo(MONDAY.atTime(9, 0));
		assertThat(blocksOf(report).get(0).getStartAt()).isEqualTo(MONDAY.atTime(10, 0));
	}

	@Test
	void aFrozenTaskThatWasShortenedIsPlannedAfresh() {
		freezeFor(120);
		Task report = givenTask("Report", 120, Priority.MEDIUM, 17);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);

		report.setEstimatedMinutes(60);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);

		assertThat(blocksOf(report)).singleElement()
			.satisfies((block) -> assertThat(block.getEndAt()).isEqualTo(MONDAY.atTime(10, 0)));
	}

	@Test
	void theWindowIsPartOfTheConfigurationAndOffUnlessSet() throws Exception {
		Cookie cookie = new AuthTestSupport(this.mvc).login();
		this.mvc.perform(get("/api/v1/config").cookie(cookie)).andExpect(jsonPath("$.freezeMinutes").value(0));

		this.mvc
			.perform(put("/api/v1/config").cookie(cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"workingHours":[{"day":"MONDAY","startTime":"09:00","endTime":"18:00"}],"blockedPeriods":[],
							"horizonDays":14,"minChunkMinutes":30,"bufferMinutes":0,"freezeMinutes":60}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.freezeMinutes").value(60));
	}

}
