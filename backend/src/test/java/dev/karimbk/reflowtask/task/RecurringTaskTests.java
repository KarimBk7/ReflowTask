package dev.karimbk.reflowtask.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.karimbk.reflowtask.SettableClock;
import dev.karimbk.reflowtask.schedule.TimeBlock;
import dev.karimbk.reflowtask.schedule.TimeBlockRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Repeating tasks: one open occurrence at a time, and finishing it brings the next, due one step
 * later and not planned before the previous one was due.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecurringTaskTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	private static final LocalDate FRIDAY = MONDAY.plusDays(4);

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
	private TaskRepository tasks;

	@Autowired
	private TimeBlockRepository blocks;

	@Autowired
	private SettableClock clock;

	private Cookie cookie;

	@BeforeEach
	void logIn() throws Exception {
		this.clock.set(MONDAY.atTime(8, 0));
		this.cookie = new AuthTestSupport(this.mvc).login();
	}

	private long create(String title, LocalDate deadline, String recurrence) throws Exception {
		String response = this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"%s","estimatedMinutes":60,"priority":"MEDIUM","deadlineDate":"%s","recurrence":"%s"}"""
						.formatted(title, deadline, recurrence)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.recurrence").value(recurrence))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private void markDone(long id) throws Exception {
		this.mvc
			.perform(patch("/api/v1/tasks/" + id + "/status").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"DONE\"}"))
			.andExpect(status().isOk());
	}

	private List<Task> open(String title) {
		return this.tasks.findByUserIdAndStatusNot(1L, TaskStatus.DONE)
			.stream()
			.filter((task) -> task.getTitle().equals(title))
			.toList();
	}

	@Test
	void finishingAWeeklyTaskBringsNextWeeksWhichIsNotPlannedEarly() throws Exception {
		long first = create("Weekly review", FRIDAY, "WEEKLY");

		markDone(first);

		List<Task> next = open("Weekly review");
		assertThat(next).hasSize(1);
		Task following = next.get(0);
		assertThat(following.getDeadline()).isEqualTo(FRIDAY.plusWeeks(1).atTime(23, 59));
		assertThat(following.getNotBefore()).isEqualTo(FRIDAY.atTime(23, 59));
		assertThat(following.getRecurrence()).isEqualTo(Recurrence.WEEKLY);
		// The rule moved on: the finished one is history and never spawns a second successor.
		assertThat(this.tasks.findById(first).orElseThrow().getRecurrence()).isNull();
		// This week has plenty of room, but next week's review goes into next week.
		List<TimeBlock> placed = this.blocks.findByTaskId(following.getId());
		assertThat(placed).isNotEmpty()
			.allSatisfy((block) -> assertThat(block.getStartAt()).isAfterOrEqualTo(FRIDAY.plusDays(3).atStartOfDay()));
	}

	@Test
	void aDailyTaskSkipsDaysWithoutWorkingHours() throws Exception {
		long friday = create("Inbox zero", FRIDAY, "DAILY");

		markDone(friday);

		assertThat(open("Inbox zero")).singleElement()
			.satisfies((next) -> assertThat(next.getDeadline()).isEqualTo(FRIDAY.plusDays(3).atTime(23, 59)));
	}

	@Test
	void periodsThatAlreadyWentByAreSkippedNotOwed() throws Exception {
		long monday = create("Stretch", MONDAY, "DAILY");
		this.clock.set(MONDAY.plusDays(2).atTime(10, 0));

		markDone(monday);

		assertThat(open("Stretch")).singleElement().satisfies((next) -> {
			assertThat(next.getDeadline()).isEqualTo(MONDAY.plusDays(2).atTime(23, 59));
			assertThat(next.getNotBefore()).isEqualTo(MONDAY.plusDays(1).atTime(23, 59));
		});
	}

	@Test
	void finishingItPartByPartAlsoBringsTheNextOne() throws Exception {
		long first = create("Water plants", MONDAY, "WEEKLY");
		TimeBlock part = this.blocks.findByTaskId(first).get(0);
		this.clock.set(part.getEndAt());

		this.mvc.perform(post("/api/v1/schedule/blocks/" + part.getId() + "/done").cookie(this.cookie))
			.andExpect(status().isOk());

		assertThat(open("Water plants")).singleElement()
			.satisfies((next) -> assertThat(next.getDeadline()).isEqualTo(MONDAY.plusWeeks(1).atTime(23, 59)));
	}

	@Test
	void removingTheRuleStopsTheSeries() throws Exception {
		long only = create("Temporary", FRIDAY, "WEEKLY");
		this.mvc
			.perform(put("/api/v1/tasks/" + only).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Temporary","estimatedMinutes":60,"priority":"MEDIUM","deadlineDate":"%s"}"""
						.formatted(FRIDAY)))
			.andExpect(jsonPath("$.recurrence").doesNotExist());

		markDone(only);

		assertThat(open("Temporary")).isEmpty();
	}

	@Test
	void aRuleNeedsADeadlineAndCannotBeFixedAtATime() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"No anchor","estimatedMinutes":60,"priority":"MEDIUM","recurrence":"DAILY"}"""))
			.andExpect(status().isBadRequest());
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Standup","estimatedMinutes":15,"priority":"MEDIUM","deadlineDate":"%s",\
							"fixedStart":"%s","recurrence":"DAILY"}""".formatted(FRIDAY, MONDAY.atTime(9, 0))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theNextOccurrenceFollowsWhateverWasLastEdited() {
		Task first = new Task(1L, "Report", "notes", 90, FRIDAY.atTime(12, 0), true, Priority.HIGH,
				MONDAY.atTime(7, 0));
		first.setRecurrence(Recurrence.BIWEEKLY);

		Task next = first.nextOccurrence(FRIDAY.plusWeeks(2).atTime(12, 0), MONDAY.atTime(9, 0));

		assertThat(next.getTitle()).isEqualTo("Report");
		assertThat(next.getDescription()).isEqualTo("notes");
		assertThat(next.getEstimatedMinutes()).isEqualTo(90);
		assertThat(next.getPriority()).isEqualTo(Priority.HIGH);
		assertThat(next.isDeadlineHasTime()).isTrue();
		assertThat(next.getNotBefore()).isEqualTo(FRIDAY.atTime(12, 0));
		assertThat(next.getStatus()).isEqualTo(TaskStatus.OPEN);
	}

}
