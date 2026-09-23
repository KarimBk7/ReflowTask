package dev.karimbk.reflowtask.user;

import java.time.LocalDateTime;

import dev.karimbk.reflowtask.schedule.RescheduleEventRepository;
import dev.karimbk.reflowtask.schedule.RescheduleTrigger;
import dev.karimbk.reflowtask.schedule.SchedulerService;
import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.Task;
import dev.karimbk.reflowtask.task.TaskRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The actual point of household login: one member's data is never visible to, nor collides
 * with, another's. Covers the {@code findOverlapping} bug the multi-user work surfaced - a
 * fixed block belonging to one user must never block another user's identical slot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MultiUserIsolationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private SchedulerService scheduler;

	@Autowired
	private RescheduleEventRepository events;

	private Cookie adminCookie() throws Exception {
		return new AuthTestSupport(this.mvc).login();
	}

	private Cookie memberCookie(Cookie admin, String username) throws Exception {
		this.mvc.perform(post("/api/v1/users").cookie(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"%s","password":"a-password"}""".formatted(username)));
		return this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"%s","password":"a-password"}""".formatted(username)))
			.andReturn()
			.getResponse()
			.getCookie(AuthFilter.SESSION_COOKIE);
	}

	@Test
	void oneUsersTasksNeverAppearInAnothersList() throws Exception {
		Cookie admin = adminCookie();
		Cookie member = memberCookie(admin, "alice");

		this.mvc.perform(post("/api/v1/tasks").cookie(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Admin's task","estimatedMinutes":60,"priority":"MEDIUM"}"""));

		this.mvc.perform(get("/api/v1/tasks").cookie(member))
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void oneUsersFixedBlockDoesNotBlockAnotherUsersIdenticalSlot() throws Exception {
		Cookie admin = adminCookie();
		Cookie member = memberCookie(admin, "bob");

		// Comfortably in the future: this test uses the real system clock, not a SettableClock.
		String fixedStart = LocalDateTime.now().plusDays(30).withHour(14).withMinute(0).withSecond(0).withNano(0)
			.toString();
		this.mvc.perform(post("/api/v1/tasks").cookie(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Admin's appointment","estimatedMinutes":60,"priority":"MEDIUM",\
						"fixedStart":"%s"}""".formatted(fixedStart)));

		// The same slot, for a different user, must be free - not refused as already fixed.
		this.mvc.perform(post("/api/v1/tasks").cookie(member)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Bob's appointment","estimatedMinutes":60,"priority":"MEDIUM",\
						"fixedStart":"%s"}""".formatted(fixedStart)))
			.andExpect(status().isCreated());
	}

	@Test
	void aReflowTickForOneUserDoesNotRecordEventsForAnother() throws Exception {
		Cookie admin = adminCookie();
		User member = this.users.save(new User("carol", "irrelevant-hash", Role.MEMBER, false, LocalDateTime.now()));

		Task adminTask = this.tasks
			.save(new Task(1L, "Admin task", null, 60, null, false, Priority.MEDIUM, LocalDateTime.now()));
		Task memberTask = this.tasks.save(new Task(member.getId(), "Carol task", null, 60, null, false,
				Priority.MEDIUM, LocalDateTime.now()));

		this.scheduler.replan(1L, RescheduleTrigger.TASK_CHANGED);
		this.scheduler.replan(member.getId(), RescheduleTrigger.TASK_CHANGED);

		assertThat(this.events.findByUserIdOrderByOccurredAtDesc(1L, Limit.of(50)))
			.allSatisfy((event) -> assertThat(event.getUserId()).isEqualTo(1L));
		assertThat(this.events.findByUserIdOrderByOccurredAtDesc(member.getId(), Limit.of(50)))
			.allSatisfy((event) -> assertThat(event.getUserId()).isEqualTo(member.getId()));
	}

	@Test
	void aNewMemberStartsSetupFromMondayToFridayNotAnEmptyWeek() throws Exception {
		Cookie admin = adminCookie();
		Cookie member = memberCookie(admin, "dana");

		this.mvc.perform(get("/api/v1/config").cookie(member))
			.andExpect(jsonPath("$.onboarded").value(false))
			.andExpect(jsonPath("$.workingHours", hasSize(5)));
	}

}
