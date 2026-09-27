package dev.karimbk.reflowtask.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import com.jayway.jsonpath.JsonPath;
import dev.karimbk.reflowtask.SettableClock;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One task waiting for another: the second is never planned before the first is over. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DependencyTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

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

	private Cookie cookie;

	@BeforeEach
	void logIn() throws Exception {
		this.clock.set(MONDAY.atTime(8, 0));
		this.cookie = new AuthTestSupport(this.mvc).login();
	}

	private static String body(String title, int minutes, String priority, Long after) {
		return """
				{"title":"%s","estimatedMinutes":%d,"priority":"%s","afterTaskId":%s}"""
			.formatted(title, minutes, priority, after == null ? "null" : after.toString());
	}

	private long create(String title, int minutes, String priority, Long after) throws Exception {
		String response = this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(title, minutes, priority, after)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private LocalDateTime nextStart(long id) throws Exception {
		String response = this.mvc.perform(get("/api/v1/tasks/" + id).cookie(this.cookie))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return LocalDateTime.parse(JsonPath.read(response, "$.nextStartAt"));
	}

	@Test
	void aTaskStartsOnlyOnceTheTaskItWaitsForIsOver() throws Exception {
		long buy = create("Buy paint", 120, "LOW", null);
		long paint = create("Paint the wall", 60, "HIGH", buy);

		this.mvc.perform(get("/api/v1/tasks/" + paint).cookie(this.cookie))
			.andExpect(jsonPath("$.afterTaskId").value(buy));
		assertThat(nextStart(buy)).isEqualTo(MONDAY.atTime(9, 0));
		assertThat(nextStart(paint)).isEqualTo(MONDAY.atTime(11, 0));
	}

	@Test
	void tasksCannotWaitForEachOther() throws Exception {
		long first = create("First", 60, "MEDIUM", null);
		long second = create("Second", 60, "MEDIUM", first);

		this.mvc
			.perform(put("/api/v1/tasks/" + first).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body("First", 60, "MEDIUM", second)))
			.andExpect(status().isBadRequest());
		this.mvc
			.perform(put("/api/v1/tasks/" + first).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body("First", 60, "MEDIUM", first)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void waitingForATaskThatDoesNotExistIsRefused() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body("Orphan", 60, "MEDIUM", 999_999L)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void deletingTheTaskWaitedForLetsTheOtherGoAhead() throws Exception {
		long buy = create("Buy paint", 120, "LOW", null);
		long paint = create("Paint the wall", 60, "HIGH", buy);

		this.mvc.perform(delete("/api/v1/tasks/" + buy).cookie(this.cookie)).andExpect(status().isNoContent());

		assertThat(nextStart(paint)).isEqualTo(MONDAY.atTime(9, 0));
	}

}
