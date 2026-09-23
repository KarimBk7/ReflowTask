package dev.karimbk.reflowtask.task;

import com.jayway.jsonpath.JsonPath;
import dev.karimbk.reflowtask.user.AuthTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request JSON is assembled by hand rather than through an object mapper so these tests
 * assert on the wire format a client actually sends, independent of server-side mapping.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskApiTests {

	@Autowired
	private MockMvc mvc;

	private Cookie cookie;

	@BeforeEach
	void logIn() throws Exception {
		this.cookie = new AuthTestSupport(this.mvc).login();
	}

	private static String json(String title, String minutes, String deadlineDate, String deadlineTime,
			String priority) {
		return """
				{"title":%s,"estimatedMinutes":%s,"deadlineDate":%s,"deadlineTime":%s,"priority":%s}"""
			.formatted(quote(title), minutes, quote(deadlineDate), quote(deadlineTime), quote(priority));
	}

	private static String quote(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private long create(String body) throws Exception {
		String response = this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	@Test
	void createsTaskAsOpenWithLocationHeader() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json("Write the scheduler", "120", null, null, "HIGH")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").exists())
			.andExpect(jsonPath("$.title").value("Write the scheduler"))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.deadline").doesNotExist());
	}

	@Test
	void aDateOnlyDeadlineBecomesTheLastMinuteOfThatDay() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json("Due Friday", "60", "2026-09-18", null, "MEDIUM")))
			.andExpect(jsonPath("$.deadline").value("2026-09-18T23:59:00"))
			.andExpect(jsonPath("$.deadlineHasTime").value(false));
	}

	@Test
	void anExplicitDeadlineTimeIsKeptAndFlagged() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json("Standup", "15", "2026-09-18", "09:30:00", "LOW")))
			.andExpect(jsonPath("$.deadline").value("2026-09-18T09:30:00"))
			.andExpect(jsonPath("$.deadlineHasTime").value(true));
	}

	@Test
	void rejectsADeadlineTimeWithoutADate() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json("Nonsense", "30", null, "09:30:00", "LOW")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.deadlineConsistent").exists());
	}

	@Test
	void rejectsBlankTitleAndNonPositiveDuration() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json("   ", "0", null, null, "LOW")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title").exists())
			.andExpect(jsonPath("$.errors.estimatedMinutes").exists());
	}

	@Test
	void updateChangesEditableFieldsAndLeavesStatusAlone() throws Exception {
		long id = create(json("A task", "60", null, null, "MEDIUM"));

		this.mvc
			.perform(put("/api/v1/tasks/" + id).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json("Renamed", "45", null, null, "HIGH")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Renamed"))
			.andExpect(jsonPath("$.estimatedMinutes").value(45))
			.andExpect(jsonPath("$.priority").value("HIGH"))
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void clearingTheDeadlineAlsoClearsTheTimeFlag() throws Exception {
		long id = create(json("Dated", "60", "2026-09-18", "09:30:00", "MEDIUM"));

		this.mvc
			.perform(put("/api/v1/tasks/" + id).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json("No longer dated", "60", null, null, "LOW")))
			.andExpect(jsonPath("$.deadline").doesNotExist())
			.andExpect(jsonPath("$.deadlineHasTime").value(false));
	}

	@Test
	void statusChangesThroughItsOwnEndpoint() throws Exception {
		long id = create(json("A task", "60", null, null, "MEDIUM"));

		this.mvc
			.perform(patch("/api/v1/tasks/" + id + "/status").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"DONE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void deleteThenFetchIsNotFound() throws Exception {
		long id = create(json("A task", "60", null, null, "MEDIUM"));

		this.mvc.perform(delete("/api/v1/tasks/" + id).cookie(this.cookie)).andExpect(status().isNoContent());
		this.mvc.perform(get("/api/v1/tasks/" + id).cookie(this.cookie)).andExpect(status().isNotFound());
	}

	@Test
	void missingTaskIsAProblemDetail() throws Exception {
		this.mvc.perform(get("/api/v1/tasks/999999").cookie(this.cookie))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Task 999999 not found"));
	}

	/**
	 * Regression: the week view used to work out whether a task was scheduled from the blocks
	 * of the week on screen, so looking at any other week reported every task placed elsewhere
	 * as having "no slot in the horizon". Placement is derived here from all of a task's
	 * blocks, independent of which dates a client happens to be looking at.
	 */
	@Test
	void everyTaskReportsItsScheduledMinutesWhereverItsBlocksAre() throws Exception {
		create(json("First", "60", null, null, "MEDIUM"));
		create(json("Second", "90", null, null, "MEDIUM"));

		this.mvc.perform(get("/api/v1/tasks").cookie(this.cookie))
			.andExpect(jsonPath("$[?(@.title == 'First')].scheduledMinutes").value(60))
			.andExpect(jsonPath("$[?(@.title == 'Second')].scheduledMinutes").value(90));
	}

	@Test
	void aTaskThatCannotMeetItsDeadlineIsReportedAtRisk() throws Exception {
		// A deadline already in the past cannot be met by any placement, whatever the clock says.
		create(json("Overdue", "60", "2020-01-01", null, "HIGH"));

		this.mvc.perform(get("/api/v1/tasks").cookie(this.cookie))
			.andExpect(jsonPath("$[?(@.title == 'Overdue')].atRisk").value(true));
	}

	@Test
	void workBeyondTheHorizonReportsTheShortfall() throws Exception {
		// Thirty days of effort cannot fit a fourteen-day horizon of working hours.
		long id = create(json("Enormous", "43200", null, null, "LOW"));

		String body = this.mvc.perform(get("/api/v1/tasks/" + id).cookie(this.cookie))
			.andReturn()
			.getResponse()
			.getContentAsString();
		int scheduled = JsonPath.read(body, "$.scheduledMinutes");
		assertThat(scheduled).isPositive().isLessThan(43200);
	}

	@Test
	void theCreateResponseAlreadyReflectsTheReplan() throws Exception {
		// The response is built after the replan, so a client does not have to re-fetch to learn
		// where the task it just created was placed.
		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json("Placed at once", "60", null, null, "MEDIUM")))
			.andExpect(jsonPath("$.scheduledMinutes").value(60))
			.andExpect(jsonPath("$.atRisk").value(false));
	}

	@Test
	void listReturnsEveryTask() throws Exception {
		create(json("First", "60", null, null, "MEDIUM"));
		create(json("Second", "60", null, null, "MEDIUM"));

		this.mvc.perform(get("/api/v1/tasks").cookie(this.cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)));
	}

}
