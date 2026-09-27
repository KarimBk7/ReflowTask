package dev.karimbk.reflowtask.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import com.jayway.jsonpath.JsonPath;
import dev.karimbk.reflowtask.SettableClock;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Not before": work that cannot start yet, such as a task waiting for a delivery on Wednesday. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EarliestStartTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);

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
	private TimeBlockRepository blocks;

	@Autowired
	private SettableClock clock;

	private Cookie cookie;

	@BeforeEach
	void logIn() throws Exception {
		this.clock.set(MONDAY.atTime(8, 0));
		this.cookie = new AuthTestSupport(this.mvc).login();
	}

	private static String body(String notBefore, String deadlineDate) {
		return """
				{"title":"Assemble shelf","estimatedMinutes":60,"priority":"MEDIUM","notBefore":%s,"deadlineDate":%s}"""
			.formatted(notBefore == null ? "null" : "\"" + notBefore + "\"",
					deadlineDate == null ? "null" : "\"" + deadlineDate + "\"");
	}

	@Test
	void workWaitsForItsEarliestStartThoughTheWeekIsEmpty() throws Exception {
		String response = this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(WEDNESDAY.atStartOfDay() + ":00", null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.notBefore").value(WEDNESDAY.atStartOfDay() + ":00"))
			.andExpect(jsonPath("$.nextStartAt").value(WEDNESDAY.atTime(9, 0) + ":00"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(response, "$.id")).longValue();

		// Saving without it lifts the wait: an update replaces every field.
		this.mvc
			.perform(put("/api/v1/tasks/" + id).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(null, null)))
			.andExpect(jsonPath("$.notBefore").doesNotExist())
			.andExpect(jsonPath("$.nextStartAt").value(MONDAY.atTime(9, 0) + ":00"));
		assertThat(this.blocks.findByTaskId(id)).hasSize(1);
	}

	@Test
	void anEarliestStartAfterTheDeadlineIsRefused() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(WEDNESDAY.atStartOfDay() + ":00", MONDAY.plusDays(1).toString())))
			.andExpect(status().isBadRequest());
	}

}
