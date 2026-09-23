package dev.karimbk.reflowtask.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import dev.karimbk.reflowtask.SettableClock;
import dev.karimbk.reflowtask.schedule.RescheduleEventRepository;
import dev.karimbk.reflowtask.schedule.RescheduleTrigger;
import dev.karimbk.reflowtask.schedule.TimeBlockRepository;
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
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfigApiTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	/** The admin account seeded by V4__household_users.sql, which every test logs in as. */
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
	private MockMvc mvc;

	@Autowired
	private SettableClock clock;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private TimeBlockRepository blocks;

	@Autowired
	private RescheduleEventRepository events;

	private Cookie cookie;

	@BeforeEach
	void resetTime() throws Exception {
		this.clock.set(MONDAY.atTime(8, 0));
		this.cookie = new AuthTestSupport(this.mvc).login();
	}

	private static String window(String day, String start, String end) {
		return """
				{"day":"%s","startTime":"%s","endTime":"%s"}""".formatted(day, start, end);
	}

	private static String config(String workingHours, String blockedPeriods, Object horizon, Object minChunk) {
		return config(workingHours, blockedPeriods, horizon, minChunk, 0);
	}

	private static String config(String workingHours, String blockedPeriods, Object horizon, Object minChunk,
			Object buffer) {
		return """
				{"workingHours":[%s],"blockedPeriods":[%s],"horizonDays":%s,"minChunkMinutes":%s,"bufferMinutes":%s}"""
			.formatted(workingHours, blockedPeriods, horizon, minChunk, buffer);
	}

	@Test
	void aFreshInstallImposesNoBreaks() throws Exception {
		// A break is the owner's decision. The lunch V2 once seeded is gone, and nothing replaced it.
		this.mvc.perform(get("/api/v1/config").cookie(this.cookie))
			.andExpect(jsonPath("$.blockedPeriods", hasSize(0)))
			.andExpect(jsonPath("$.workingHours", hasSize(5)));
	}

	@Test
	void aFreshInstallIsNotYetOnboarded() throws Exception {
		this.mvc.perform(get("/api/v1/config").cookie(this.cookie)).andExpect(jsonPath("$.onboarded").value(false));
	}

	@Test
	void savingTheConfigurationStoresTheBufferAndOnboardsTheOwner() throws Exception {
		this.mvc
			.perform(put("/api/v1/config").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(config(window("MONDAY", "09:00", "18:00"), "", 14, 30, 15)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bufferMinutes").value(15))
			.andExpect(jsonPath("$.onboarded").value(true));

		this.mvc.perform(get("/api/v1/config").cookie(this.cookie))
			.andExpect(jsonPath("$.bufferMinutes").value(15))
			.andExpect(jsonPath("$.onboarded").value(true));
	}

	/**
	 * The one thing this product does that no plain task list can is invisible until something
	 * is actually missed. A first-run owner should not have to wait days for that to happen by
	 * chance, so finishing setup seeds one example already missed, and the replan setup already
	 * triggers picks it up through the ordinary miss-detection path - no special-cased UI.
	 */
	@Test
	void finishingSetupForTheFirstTimeDemonstratesAMissLiveInTheActivity() throws Exception {
		this.mvc.perform(put("/api/v1/config").cookie(this.cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(config(window("MONDAY", "09:00", "18:00"), "", 14, 30)));

		this.mvc.perform(get("/api/v1/reschedule-events").cookie(this.cookie))
			.andExpect(jsonPath("$[0].items[0].kind").value("MISSED"))
			.andExpect(jsonPath("$[0].items[0].taskTitle").value("See how this works: I was missed"));
	}

	@Test
	void finishingSetupASecondTimeDoesNotSeedAnotherDemo() throws Exception {
		String body = config(window("MONDAY", "09:00", "18:00"), "", 14, 30);
		this.mvc.perform(put("/api/v1/config").cookie(this.cookie).contentType(MediaType.APPLICATION_JSON).content(body));
		long afterFirst = this.tasks.count();

		this.mvc.perform(put("/api/v1/config").cookie(this.cookie).contentType(MediaType.APPLICATION_JSON).content(body));

		assertThat(this.tasks.count()).isEqualTo(afterFirst);
	}

	@Test
	void aClientCannotUndoOnboardingBySendingTheFlag() throws Exception {
		String body = """
				{"workingHours":[%s],"blockedPeriods":[],"horizonDays":14,"minChunkMinutes":30,"bufferMinutes":0,"onboarded":false}"""
			.formatted(window("MONDAY", "09:00", "18:00"));

		this.mvc.perform(put("/api/v1/config").cookie(this.cookie).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(jsonPath("$.onboarded").value(true));
	}

	@Test
	void rejectsABufferOutOfRange() throws Exception {
		this.mvc
			.perform(put("/api/v1/config").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(config(window("MONDAY", "09:00", "18:00"), "", 14, 30, 500)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.bufferMinutes").exists());
	}

	@Test
	void theBufferIsAppliedWhenTheScheduleIsReplanned() throws Exception {
		// Onboard first and clear what that seeds (its own test covers it), so this test's two
		// tasks are the only thing competing for capacity below.
		this.mvc.perform(put("/api/v1/config").cookie(this.cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(config(window("MONDAY", "09:00", "18:00"), "", 14, 30)));
		this.blocks.deleteAll();
		this.tasks.deleteAll();

		Task first = this.tasks
			.save(new Task(USER_ID, "First", null, 60, null, false, Priority.HIGH, MONDAY.atTime(7, 0)));
		Task second = this.tasks
			.save(new Task(USER_ID, "Second", null, 60, null, false, Priority.LOW, MONDAY.atTime(7, 0)));

		this.mvc.perform(put("/api/v1/config").cookie(this.cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(config(window("MONDAY", "09:00", "18:00"), "", 14, 30, 20)));

		assertThat(this.blocks.findByTaskId(first.getId())).singleElement()
			.satisfies((block) -> assertThat(block.getEndAt()).isEqualTo(MONDAY.atTime(10, 0)));
		assertThat(this.blocks.findByTaskId(second.getId())).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(10, 20)));
	}

	@Test
	void replacesTheWholeConfigurationAndReadsItBack() throws Exception {
		String body = config(window("MONDAY", "08:00", "16:00") + "," + window("TUESDAY", "10:00", "14:00"),
				"""
						{"day":"MONDAY","startTime":"11:30","endTime":"12:00","label":"Walk"}""", 7, 45);

		this.mvc.perform(put("/api/v1/config").cookie(this.cookie).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workingHours", hasSize(2)))
			.andExpect(jsonPath("$.horizonDays").value(7));

		this.mvc.perform(get("/api/v1/config").cookie(this.cookie))
			.andExpect(jsonPath("$.workingHours", hasSize(2)))
			.andExpect(jsonPath("$.workingHours[0].day").value("MONDAY"))
			.andExpect(jsonPath("$.workingHours[0].startTime").value("08:00:00"))
			.andExpect(jsonPath("$.blockedPeriods", hasSize(1)))
			.andExpect(jsonPath("$.blockedPeriods[0].label").value("Walk"))
			.andExpect(jsonPath("$.minChunkMinutes").value(45));
	}

	/**
	 * Working hours are keyed by (user, weekday), and Hibernate executes inserts before deletes
	 * when it flushes, so replacing a weekday that already has a row could collide on the
	 * primary key. The seeded defaults include Monday, so this exercises that case. It guards
	 * the behaviour rather than the mechanism: see ConfigService for why the collision does not
	 * occur.
	 */
	@Test
	void replacingAWeekdayThatAlreadyExistsDoesNotCollide() throws Exception {
		this.mvc
			.perform(put("/api/v1/config").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(config(window("MONDAY", "07:00", "15:00"), "", 14, 30)))
			.andExpect(status().isOk());

		this.mvc.perform(get("/api/v1/config").cookie(this.cookie))
			.andExpect(jsonPath("$.workingHours", hasSize(1)))
			.andExpect(jsonPath("$.workingHours[0].startTime").value("07:00:00"));
	}

	@Test
	void rejectsAWindowThatEndsBeforeItStarts() throws Exception {
		this.mvc
			.perform(put("/api/v1/config").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(config(window("MONDAY", "18:00", "09:00"), "", 14, 30)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsTheSameWeekdayTwice() throws Exception {
		this.mvc
			.perform(put("/api/v1/config").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(config(window("MONDAY", "09:00", "12:00") + "," + window("MONDAY", "13:00", "18:00"),
							"", 14, 30)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.oneWindowPerDay").exists());
	}

	@Test
	void rejectsPlanningSettingsOutOfRange() throws Exception {
		this.mvc
			.perform(put("/api/v1/config").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(config(window("MONDAY", "09:00", "18:00"), "", 0, 30)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.horizonDays").exists());
	}

	@Test
	void aRejectedChangeLeavesTheExistingConfigurationUntouched() throws Exception {
		this.mvc.perform(put("/api/v1/config").cookie(this.cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(config(window("MONDAY", "18:00", "09:00"), "", 14, 30)));

		// The seeded Monday-to-Friday hours survive a request that failed validation.
		this.mvc.perform(get("/api/v1/config").cookie(this.cookie))
			.andExpect(jsonPath("$.workingHours", hasSize(5)));
	}

	/**
	 * The point of the feature: a schedule planned against the old hours is wrong the moment
	 * they change, so saving new hours replans immediately and says why.
	 */
	@Test
	void changingWorkingHoursReplansTheScheduleAndRecordsWhy() throws Exception {
		Task task = this.tasks.save(new Task(USER_ID, "Write report", null, 60, null, false, Priority.MEDIUM,
				MONDAY.atTime(7, 0)));
		this.mvc.perform(put("/api/v1/config").cookie(this.cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(config(window("MONDAY", "09:00", "18:00"), "", 14, 30)));
		assertThat(this.blocks.findByTaskId(task.getId())).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(9, 0)));

		// Mornings stop being working time.
		this.mvc
			.perform(put("/api/v1/config").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(config(window("MONDAY", "13:00", "18:00"), "", 14, 30)))
			.andExpect(status().isOk());

		assertThat(this.blocks.findByTaskId(task.getId())).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(13, 0)));
		assertThat(this.events.findByUserIdOrderByOccurredAtDesc(USER_ID, Limit.of(1))).singleElement()
			.satisfies((event) -> assertThat(event.getTriggerType()).isEqualTo(RescheduleTrigger.CONFIG_CHANGED));
	}

}
