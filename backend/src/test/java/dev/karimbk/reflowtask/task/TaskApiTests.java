package dev.karimbk.reflowtask.task;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
			.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	@Test
	void createsTaskAsOpenWithLocationHeader() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
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
			.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
					.content(json("Due Friday", "60", "2026-09-18", null, "MEDIUM")))
			.andExpect(jsonPath("$.deadline").value("2026-09-18T23:59:00"))
			.andExpect(jsonPath("$.deadlineHasTime").value(false));
	}

	@Test
	void anExplicitDeadlineTimeIsKeptAndFlagged() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
					.content(json("Standup", "15", "2026-09-18", "09:30:00", "LOW")))
			.andExpect(jsonPath("$.deadline").value("2026-09-18T09:30:00"))
			.andExpect(jsonPath("$.deadlineHasTime").value(true));
	}

	@Test
	void rejectsADeadlineTimeWithoutADate() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
					.content(json("Nonsense", "30", null, "09:30:00", "LOW")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.deadlineConsistent").exists());
	}

	@Test
	void rejectsBlankTitleAndNonPositiveDuration() throws Exception {
		this.mvc
			.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
					.content(json("   ", "0", null, null, "LOW")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title").exists())
			.andExpect(jsonPath("$.errors.estimatedMinutes").exists());
	}

	@Test
	void updateChangesEditableFieldsAndLeavesStatusAlone() throws Exception {
		long id = create(json("A task", "60", null, null, "MEDIUM"));

		this.mvc
			.perform(put("/api/v1/tasks/" + id).contentType(MediaType.APPLICATION_JSON)
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
			.perform(put("/api/v1/tasks/" + id).contentType(MediaType.APPLICATION_JSON)
					.content(json("No longer dated", "60", null, null, "LOW")))
			.andExpect(jsonPath("$.deadline").doesNotExist())
			.andExpect(jsonPath("$.deadlineHasTime").value(false));
	}

	@Test
	void statusChangesThroughItsOwnEndpoint() throws Exception {
		long id = create(json("A task", "60", null, null, "MEDIUM"));

		this.mvc
			.perform(patch("/api/v1/tasks/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"DONE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void deleteThenFetchIsNotFound() throws Exception {
		long id = create(json("A task", "60", null, null, "MEDIUM"));

		this.mvc.perform(delete("/api/v1/tasks/" + id)).andExpect(status().isNoContent());
		this.mvc.perform(get("/api/v1/tasks/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void missingTaskIsAProblemDetail() throws Exception {
		this.mvc.perform(get("/api/v1/tasks/999999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Task 999999 not found"));
	}

	@Test
	void listReturnsEveryTask() throws Exception {
		create(json("First", "60", null, null, "MEDIUM"));
		create(json("Second", "60", null, null, "MEDIUM"));

		this.mvc.perform(get("/api/v1/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
	}

}
