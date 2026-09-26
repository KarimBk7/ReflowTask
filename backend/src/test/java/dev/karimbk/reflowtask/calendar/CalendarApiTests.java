package dev.karimbk.reflowtask.calendar;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import dev.karimbk.reflowtask.SettableClock;
import dev.karimbk.reflowtask.schedule.RescheduleTrigger;
import dev.karimbk.reflowtask.schedule.SchedulerService;
import dev.karimbk.reflowtask.schedule.TimeBlockRepository;
import dev.karimbk.reflowtask.user.AuthTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calendar sync end to end: the subscription feed calendar apps poll, and other calendars read
 * over HTTP (a small local server stands in for Nextcloud) whose events the scheduler plans around.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarApiTests {

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

	@Autowired
	private TimeBlockRepository blocks;

	private AuthTestSupport auth;

	private Cookie admin;

	private HttpServer server;

	private volatile String served;

	private volatile int status;

	@BeforeEach
	void setUp() throws Exception {
		this.clock.set(MONDAY.atTime(8, 0));
		this.auth = new AuthTestSupport(this.mvc);
		this.admin = this.auth.login();
		this.status = 200;
		this.served = calendarWith("Standup", 9, 0, 10, 0);
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/cal.ics", (exchange) -> {
			byte[] body = this.served.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(this.status, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		this.server.start();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	private String url() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort() + "/cal.ics";
	}

	/** One event on MONDAY, given in Berlin local time. */
	private static String calendarWith(String title, int fromHour, int fromMinute, int toHour, int toMinute) {
		String day = MONDAY.toString().replace("-", "");
		return """
				BEGIN:VCALENDAR\r
				VERSION:2.0\r
				PRODID:-//Test//EN\r
				BEGIN:VEVENT\r
				UID:ext-1\r
				DTSTAMP:20260901T000000Z\r
				SUMMARY:%s\r
				DTSTART;TZID=Europe/Berlin:%sT%02d%02d00\r
				DTEND;TZID=Europe/Berlin:%sT%02d%02d00\r
				END:VEVENT\r
				END:VCALENDAR\r
				""".formatted(title, day, fromHour, fromMinute, day, toHour, toMinute);
	}

	private long createTask(Cookie as, String title, int minutes) throws Exception {
		String body = this.mvc
			.perform(post("/api/v1/tasks").cookie(as)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"title\":\"%s\",\"estimatedMinutes\":%d,\"priority\":\"MEDIUM\"}".formatted(title, minutes)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String firstStart(long taskId) {
		return this.blocks.findByTaskId(taskId).get(0).getStartAt().toLocalTime().toString();
	}

	private long addSource(Cookie as) throws Exception {
		String body = this.mvc
			.perform(post("/api/v1/calendar/sources").cookie(as)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Work\",\"url\":\"%s\"}".formatted(url())))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String feedPath(Cookie as) throws Exception {
		String body = this.mvc.perform(post("/api/v1/calendar/feed").cookie(as)).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.path");
	}

	// --- out: the subscription feed ----------------------------------------------------

	@Test
	void theFeedIsOffUntilTurnedOnAndThenReadableWithoutLoggingIn() throws Exception {
		createTask(this.admin, "Write report", 60);
		this.mvc.perform(get("/api/v1/calendar/feed").cookie(this.admin)).andExpect(jsonPath("$.path").value(nullValue()));

		String path = feedPath(this.admin);

		assertThat(path).startsWith("/api/v1/calendar/feed/").endsWith(".ics");
		this.mvc.perform(get(path))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/calendar"))
			.andExpect(content().string(containsString("SUMMARY:Write report")))
			.andExpect(content().string(containsString("BEGIN:VCALENDAR")));
	}

	@Test
	void aNewLinkRetiresTheOldOneAndTurningItOffRetiresAny() throws Exception {
		String first = feedPath(this.admin);
		String second = feedPath(this.admin);

		this.mvc.perform(get(first)).andExpect(status().isNotFound());
		this.mvc.perform(get(second)).andExpect(status().isOk());

		this.mvc.perform(delete("/api/v1/calendar/feed").cookie(this.admin)).andExpect(status().isNoContent());
		this.mvc.perform(get(second)).andExpect(status().isNotFound());
	}

	@Test
	void eachFeedShowsOnlyItsOwnersWork() throws Exception {
		createTask(this.admin, "Admin work", 60);
		Cookie member = this.auth.memberSession(this.admin, "feeder");
		createTask(member, "Member work", 60);

		this.mvc.perform(get(feedPath(member)))
			.andExpect(content().string(containsString("Member work")))
			.andExpect(content().string(not(containsString("Admin work"))));
	}

	@Test
	void anUnknownFeedAddressIsNotFound() throws Exception {
		this.mvc.perform(get("/api/v1/calendar/feed/not-a-real-token.ics")).andExpect(status().isNotFound());
	}

	// --- in: other calendars as busy time -----------------------------------------------

	@Test
	void anotherCalendarsEventBlocksItsTimeAndWorkIsPlannedAroundIt() throws Exception {
		long task = createTask(this.admin, "Deep work", 60);
		assertThat(firstStart(task)).isEqualTo("09:00");

		addSource(this.admin);

		assertThat(firstStart(task)).isEqualTo("10:00");
		this.mvc.perform(get("/api/v1/calendar/busy?from=%s&to=%s".formatted(MONDAY.atStartOfDay(), MONDAY.plusDays(1).atStartOfDay()))
				.cookie(this.admin))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].title").value("Standup"))
			.andExpect(jsonPath("$[0].startAt").value(MONDAY.atTime(9, 0) + ":00"));
	}

	@Test
	void removingTheCalendarFreesItsTimeAgain() throws Exception {
		long task = createTask(this.admin, "Deep work", 60);
		long source = addSource(this.admin);
		assertThat(firstStart(task)).isEqualTo("10:00");

		this.mvc.perform(delete("/api/v1/calendar/sources/" + source).cookie(this.admin)).andExpect(status().isNoContent());

		assertThat(firstStart(task)).isEqualTo("09:00");
		this.mvc.perform(get("/api/v1/calendar/sources").cookie(this.admin)).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void refreshingPicksUpChangesInTheOtherCalendar() throws Exception {
		long task = createTask(this.admin, "Deep work", 60);
		addSource(this.admin);

		this.served = calendarWith("Standup moved", 9, 0, 11, 0);
		this.mvc.perform(post("/api/v1/calendar/sources/refresh").cookie(this.admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].lastError").value(nullValue()));

		assertThat(firstStart(task)).isEqualTo("11:00");
	}

	@Test
	void aCalendarThatStopsAnsweringKeepsItsLastBusyTimeAndSaysWhy() throws Exception {
		long task = createTask(this.admin, "Deep work", 60);
		addSource(this.admin);

		this.status = 500;
		this.mvc.perform(post("/api/v1/calendar/sources/refresh").cookie(this.admin))
			.andExpect(jsonPath("$[0].lastError").value(containsString("500")));

		assertThat(firstStart(task)).isEqualTo("10:00");
	}

	@Test
	void anAddressThatIsNotACalendarIsRefusedAndNothingIsKept() throws Exception {
		this.served = "<html>Please log in</html>";

		this.mvc.perform(post("/api/v1/calendar/sources").cookie(this.admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Oops\",\"url\":\"%s\"}".formatted(url())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("calendar")));
		this.mvc.perform(get("/api/v1/calendar/sources").cookie(this.admin)).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void unreachableErroringAndNonWebAddressesAreRefused() throws Exception {
		this.status = 404;
		for (String bad : new String[] { url(), "http://127.0.0.1:1/nothing.ics", "file:///etc/passwd",
				"ftp://example.com/cal.ics", "not a url" }) {
			this.mvc.perform(post("/api/v1/calendar/sources").cookie(this.admin)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Bad\",\"url\":\"%s\"}".formatted(bad)))
				.andExpect(status().isBadRequest());
		}
	}

	/** A webcal:// link to a server without TLS, like one on the home network, still works. */
	@Test
	void webcalAddressesFallBackToPlainHttp() throws Exception {
		String webcal = url().replace("http://", "webcal://");
		this.mvc.perform(post("/api/v1/calendar/sources").cookie(this.admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Home\",\"url\":\"%s\"}".formatted(webcal)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.url").value(webcal))
			.andExpect(jsonPath("$.lastError").value(nullValue()));
	}

	@Test
	void anotherPersonsCalendarsAndBusyTimeStayTheirs() throws Exception {
		long source = addSource(this.admin);
		Cookie member = this.auth.memberSession(this.admin, "neighbour");

		this.mvc.perform(get("/api/v1/calendar/sources").cookie(member)).andExpect(jsonPath("$", hasSize(0)));
		this.mvc.perform(get("/api/v1/calendar/busy?from=%s&to=%s".formatted(MONDAY.atStartOfDay(), MONDAY.plusDays(1).atStartOfDay()))
				.cookie(member))
			.andExpect(jsonPath("$", hasSize(0)));
		this.mvc.perform(delete("/api/v1/calendar/sources/" + source).cookie(member)).andExpect(status().isNotFound());
		long task = createTask(member, "Member work", 60);
		assertThat(firstStart(task)).isEqualTo("09:00");
	}

	@Test
	void everythingButTheFeedItselfNeedsALogin() throws Exception {
		this.mvc.perform(get("/api/v1/calendar/feed")).andExpect(status().isUnauthorized());
		this.mvc.perform(post("/api/v1/calendar/feed")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/v1/calendar/sources")).andExpect(status().isUnauthorized());
		this.mvc.perform(post("/api/v1/calendar/sources/refresh")).andExpect(status().isUnauthorized());
	}

	/** What the hourly job does for each person: read the calendars again, then replan. */
	@Test
	void anHourlyRefreshThenReplanPicksUpTheOtherCalendar() throws Exception {
		long task = createTask(this.admin, "Deep work", 60);
		addSource(this.admin);
		this.served = calendarWith("Standup moved", 9, 0, 12, 0);

		this.calendars.refresh(1L);
		this.scheduler.replan(1L, RescheduleTrigger.SCHEDULED_JOB);

		assertThat(firstStart(task)).isEqualTo("12:00");
	}

	@Autowired
	private CalendarService calendars;

	@Autowired
	private SchedulerService scheduler;

}
