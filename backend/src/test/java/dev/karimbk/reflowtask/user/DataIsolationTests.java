package dev.karimbk.reflowtask.user;

import java.time.LocalDateTime;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.karimbk.reflowtask.config.BlockedPeriodRepository;
import dev.karimbk.reflowtask.config.SchedulingSettingsRepository;
import dev.karimbk.reflowtask.config.WorkingHoursRepository;
import dev.karimbk.reflowtask.schedule.RescheduleEventRepository;
import dev.karimbk.reflowtask.schedule.TimeBlockRepository;
import dev.karimbk.reflowtask.task.TaskRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
 * The promise of a household login is that two people share a server and never each other's
 * calendar. Every endpoint that takes an id is tried with someone else's id, and every list is
 * checked for leakage. A stranger's id must look exactly like an id that does not exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DataIsolationTests {

	private static final String TASK = """
			{"title":"%s","estimatedMinutes":60,"priority":"MEDIUM","fixedStart":"%s"}""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private TimeBlockRepository blocks;

	@Autowired
	private RescheduleEventRepository events;

	@Autowired
	private WorkingHoursRepository workingHours;

	@Autowired
	private BlockedPeriodRepository blockedPeriods;

	@Autowired
	private SchedulingSettingsRepository settings;

	@Autowired
	private UserRepository users;

	private AuthTestSupport auth;

	private Cookie alice;

	private Cookie bob;

	private long aliceId;

	private long bobId;

	private String start;

	@BeforeEach
	void twoUsers() throws Exception {
		this.auth = new AuthTestSupport(this.mvc);
		Cookie admin = this.auth.login();
		this.alice = this.auth.memberSession(admin, "alice");
		this.bob = this.auth.memberSession(admin, "bob");
		this.aliceId = this.users.findByUsername("alice").orElseThrow().getId();
		this.bobId = this.users.findByUsername("bob").orElseThrow().getId();
		this.start = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0).toString();
	}

	private long createTask(Cookie as, String title) throws Exception {
		String body = this.mvc
			.perform(post("/api/v1/tasks").cookie(as)
					.contentType(MediaType.APPLICATION_JSON)
					.content(TASK.formatted(title, this.start)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private long blockOf(long taskId) {
		return this.blocks.findByTaskId(taskId).get(0).getId();
	}

	private static String range(String from, String to) {
		return "/api/v1/schedule?from=" + from + "&to=" + to;
	}

	// --- ids that belong to someone else ------------------------------------------------

	@Test
	void anotherUsersTaskIsNotFoundForEveryVerb() throws Exception {
		long task = createTask(this.alice, "Alice only");
		String json = "{\"title\":\"Hijacked\",\"estimatedMinutes\":30,\"priority\":\"HIGH\"}";

		this.mvc.perform(get("/api/v1/tasks/" + task).cookie(this.bob)).andExpect(status().isNotFound());
		this.mvc
			.perform(put("/api/v1/tasks/" + task).cookie(this.bob)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json))
			.andExpect(status().isNotFound());
		this.mvc
			.perform(patch("/api/v1/tasks/" + task + "/status").cookie(this.bob)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"DONE\"}"))
			.andExpect(status().isNotFound());
		this.mvc.perform(delete("/api/v1/tasks/" + task).cookie(this.bob)).andExpect(status().isNotFound());

		this.mvc.perform(get("/api/v1/tasks/" + task).cookie(this.alice))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Alice only"))
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void anotherUsersBlockCannotBeMovedPinnedOrUnpinned() throws Exception {
		long block = blockOf(createTask(this.alice, "Alice block"));
		LocalDateTime original = this.blocks.findById(block).orElseThrow().getStartAt();
		String move = "{\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(original.plusHours(3), original.plusHours(4));

		this.mvc
			.perform(patch("/api/v1/schedule/blocks/" + block).cookie(this.bob)
					.contentType(MediaType.APPLICATION_JSON)
					.content(move))
			.andExpect(status().isNotFound());
		this.mvc.perform(post("/api/v1/schedule/blocks/" + block + "/pin").cookie(this.bob))
			.andExpect(status().isNotFound());
		this.mvc.perform(post("/api/v1/schedule/blocks/" + block + "/unpin").cookie(this.bob))
			.andExpect(status().isNotFound());

		assertThat(this.blocks.findById(block).orElseThrow().getStartAt()).isEqualTo(original);
		assertThat(this.blocks.findById(block).orElseThrow().isPinned()).isTrue();
	}

	// --- lists never leak ---------------------------------------------------------------

	@Test
	void taskListsAndTheCalendarShowOnlyYourOwn() throws Exception {
		createTask(this.alice, "Alice one");
		createTask(this.bob, "Bob one");
		String from = LocalDateTime.now().plusDays(9).toString();
		String to = LocalDateTime.now().plusDays(12).toString();

		this.mvc.perform(get("/api/v1/tasks").cookie(this.alice))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].title").value("Alice one"));
		this.mvc.perform(get("/api/v1/tasks").cookie(this.bob))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].title").value("Bob one"));
		this.mvc.perform(get(range(from, to)).cookie(this.alice))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].taskTitle").value("Alice one"));
		this.mvc.perform(get(range(from, to)).cookie(this.bob))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].taskTitle").value("Bob one"));
	}

	@Test
	void oneUsersReplanNeverTouchesOrRecordsForAnother() throws Exception {
		createTask(this.alice, "Alice steady");
		long aliceEvents = this.events.findByUserIdOrderByOccurredAtDesc(this.aliceId, Limit.of(50)).size();
		long block = blockOf(this.tasks.findByUserIdOrderByCreatedAtDesc(this.aliceId).get(0).getId());
		LocalDateTime before = this.blocks.findById(block).orElseThrow().getStartAt();

		this.mvc.perform(post("/api/v1/schedule/replan").cookie(this.bob)).andExpect(status().isOk());
		createTask(this.bob, "Bob busy");

		assertThat(this.events.findByUserIdOrderByOccurredAtDesc(this.aliceId, Limit.of(50))).hasSize((int) aliceEvents);
		assertThat(this.blocks.findById(block).orElseThrow().getStartAt()).isEqualTo(before);
		this.mvc.perform(get("/api/v1/reschedule-events").cookie(this.alice))
			.andExpect(jsonPath("$[?(@.items[0].taskTitle == 'Bob busy')]").isEmpty());
	}

	@Test
	void eachUserSeesOnlyTheirOwnActivity() throws Exception {
		// Finishing setup replans and records the demo miss, which is an event only Alice owns.
		this.mvc.perform(put("/api/v1/config").cookie(this.alice)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"workingHours":[{"day":"MONDAY","startTime":"09:00","endTime":"17:00"}],
						 "blockedPeriods":[],"horizonDays":14,"minChunkMinutes":30,"bufferMinutes":0}"""));

		this.mvc.perform(get("/api/v1/reschedule-events").cookie(this.bob)).andExpect(jsonPath("$", hasSize(0)));
		this.mvc.perform(get("/api/v1/reschedule-events").cookie(this.alice))
			.andExpect(jsonPath("$[0].items[0].kind").value("MISSED"));
	}

	// --- each person has their own week -------------------------------------------------

	@Test
	void hoursAndBreaksAreIndependentPerUser() throws Exception {
		String aliceConfig = """
				{"workingHours":[{"day":"MONDAY","startTime":"06:00","endTime":"12:00"}],
				 "blockedPeriods":[{"day":"MONDAY","startTime":"08:00","endTime":"08:30","label":"Coffee"}],
				 "horizonDays":21,"minChunkMinutes":45,"bufferMinutes":10}""";
		String bobConfig = """
				{"workingHours":[{"day":"FRIDAY","startTime":"13:00","endTime":"20:00"}],
				 "blockedPeriods":[],"horizonDays":7,"minChunkMinutes":15,"bufferMinutes":0}""";

		this.mvc.perform(put("/api/v1/config").cookie(this.alice).contentType(MediaType.APPLICATION_JSON).content(aliceConfig))
			.andExpect(status().isOk());
		this.mvc.perform(put("/api/v1/config").cookie(this.bob).contentType(MediaType.APPLICATION_JSON).content(bobConfig))
			.andExpect(status().isOk());

		this.mvc.perform(get("/api/v1/config").cookie(this.alice))
			.andExpect(jsonPath("$.workingHours", hasSize(1)))
			.andExpect(jsonPath("$.workingHours[0].day").value("MONDAY"))
			.andExpect(jsonPath("$.blockedPeriods[0].label").value("Coffee"))
			.andExpect(jsonPath("$.horizonDays").value(21))
			.andExpect(jsonPath("$.bufferMinutes").value(10));
		this.mvc.perform(get("/api/v1/config").cookie(this.bob))
			.andExpect(jsonPath("$.workingHours[0].day").value("FRIDAY"))
			.andExpect(jsonPath("$.blockedPeriods", hasSize(0)))
			.andExpect(jsonPath("$.horizonDays").value(7));
	}

	@Test
	void savingHoursOnlyPlansThatUsersOwnWork() throws Exception {
		createTask(this.alice, "Alice fixed");
		String config = """
				{"workingHours":[{"day":"MONDAY","startTime":"09:00","endTime":"17:00"}],
				 "blockedPeriods":[],"horizonDays":14,"minChunkMinutes":30,"bufferMinutes":0}""";

		this.mvc.perform(put("/api/v1/config").cookie(this.bob).contentType(MediaType.APPLICATION_JSON).content(config))
			.andExpect(status().isOk());

		assertThat(this.tasks.findByUserIdOrderByCreatedAtDesc(this.aliceId)).extracting((task) -> task.getTitle())
			.containsExactly("Alice fixed");
	}

	@Test
	void onboardingIsPerUserAndSeedsADemoOnlyForThem() throws Exception {
		String config = """
				{"workingHours":[{"day":"MONDAY","startTime":"09:00","endTime":"17:00"}],
				 "blockedPeriods":[],"horizonDays":14,"minChunkMinutes":30,"bufferMinutes":0}""";

		this.mvc.perform(put("/api/v1/config").cookie(this.alice).contentType(MediaType.APPLICATION_JSON).content(config));

		this.mvc.perform(get("/api/v1/config").cookie(this.alice)).andExpect(jsonPath("$.onboarded").value(true));
		this.mvc.perform(get("/api/v1/config").cookie(this.bob)).andExpect(jsonPath("$.onboarded").value(false));
		assertThat(this.tasks.findByUserIdOrderByCreatedAtDesc(this.aliceId)).hasSize(1);
		assertThat(this.tasks.findByUserIdOrderByCreatedAtDesc(this.bobId)).isEmpty();
	}

	// --- deleting an account ------------------------------------------------------------

	@Test
	void removingAMemberRemovesEverythingTheyOwnAndNothingElse() throws Exception {
		Cookie admin = this.auth.login();
		createTask(this.bob, "Bob doomed");
		this.mvc.perform(put("/api/v1/config").cookie(this.bob)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"workingHours":[{"day":"MONDAY","startTime":"09:00","endTime":"17:00"}],
						 "blockedPeriods":[{"day":"MONDAY","startTime":"12:00","endTime":"13:00","label":"Lunch"}],
						 "horizonDays":14,"minChunkMinutes":30,"bufferMinutes":0}"""));
		long survivor = createTask(this.alice, "Alice survives");
		assertThat(this.events.findByUserIdOrderByOccurredAtDesc(this.bobId, Limit.of(50))).isNotEmpty();

		this.mvc.perform(delete("/api/v1/users/" + this.bobId).cookie(admin)).andExpect(status().isNoContent());
		// The cascade is the database's, so the pending delete must reach it before we look.
		this.users.flush();

		assertThat(this.tasks.findByUserIdOrderByCreatedAtDesc(this.bobId)).isEmpty();
		assertThat(this.events.findByUserIdOrderByOccurredAtDesc(this.bobId, Limit.of(50))).isEmpty();
		assertThat(this.workingHours.findByUserId(this.bobId)).isEmpty();
		assertThat(this.blockedPeriods.findByUserId(this.bobId)).isEmpty();
		assertThat(this.settings.findByUserId(this.bobId)).isEmpty();
		assertThat(this.blocks.findAllWithTaskByUserId(this.bobId)).isEmpty();
		assertThat(this.blocks.findByTaskId(survivor)).hasSize(1);
		this.mvc.perform(get("/api/v1/tasks/" + survivor).cookie(this.alice)).andExpect(status().isOk());
	}

	@Test
	void aRemovedMembersSessionStopsWorkingImmediately() throws Exception {
		Cookie admin = this.auth.login();

		this.mvc.perform(delete("/api/v1/users/" + this.bobId).cookie(admin)).andExpect(status().isNoContent());

		this.mvc.perform(get("/api/v1/tasks").cookie(this.bob)).andExpect(status().isUnauthorized());
	}

	// --- nothing works without a session -----------------------------------------------

	@Test
	void everyEndpointExceptTheLoginOnesRefusesAnonymousCallers() throws Exception {
		String json = "{\"title\":\"x\",\"estimatedMinutes\":30,\"priority\":\"LOW\"}";
		List<MockHttpServletRequestBuilder> requests = List.of(get("/api/v1/tasks"), get("/api/v1/tasks/1"),
				post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(json),
				put("/api/v1/tasks/1").contentType(MediaType.APPLICATION_JSON).content(json),
				patch("/api/v1/tasks/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"),
				delete("/api/v1/tasks/1"), get(range("2026-01-01T00:00:00", "2026-01-08T00:00:00")),
				post("/api/v1/schedule/replan"), post("/api/v1/schedule/blocks/1/pin"),
				post("/api/v1/schedule/blocks/1/unpin"),
				patch("/api/v1/schedule/blocks/1").contentType(MediaType.APPLICATION_JSON)
					.content("{\"startAt\":\"2026-01-01T09:00:00\",\"endAt\":\"2026-01-01T10:00:00\"}"),
				get("/api/v1/reschedule-events"), get("/api/v1/config"), get("/api/v1/auth/me"),
				post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
					.content("{\"password\":\"long-enough-1\"}"),
				get("/api/v1/users"),
				post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"nobody\",\"password\":\"long-enough-1\"}"),
				post("/api/v1/users/1/reset-password").contentType(MediaType.APPLICATION_JSON)
					.content("{\"password\":\"long-enough-1\"}"),
				delete("/api/v1/users/2"));

		for (MockHttpServletRequestBuilder request : requests) {
			this.mvc.perform(request).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void theLoginEndpointWorksWithoutASession() throws Exception {
		this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"nobody-here\",\"password\":\"whatever\"}"))
			.andExpect(status().isUnauthorized());
	}

}
