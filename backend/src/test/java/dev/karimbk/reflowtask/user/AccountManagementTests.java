package dev.karimbk.reflowtask.user;

import java.time.LocalDateTime;
import java.time.ZoneId;

import dev.karimbk.reflowtask.SettableClock;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Everything around an account's life that is not the board itself: what a username and password
 * may look like, changing and resetting a password, sessions ending, and guessing being slowed.
 * Each test uses its own usernames, because the login throttle lives in memory and is not rolled
 * back with the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountManagementTests {

	private static final LocalDateTime START = LocalDateTime.of(2026, 9, 23, 12, 0);

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		SettableClock testClock() {
			return new SettableClock(START, ZoneId.of("Europe/Berlin"));
		}

	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SettableClock clock;

	@Autowired
	private UserSessionRepository sessions;

	private AuthTestSupport auth;

	private Cookie admin;

	@BeforeEach
	void logInAsAdmin() throws Exception {
		this.clock.set(START);
		this.auth = new AuthTestSupport(this.mvc);
		this.admin = this.auth.login();
	}

	private int loginStatus(String username, String password) throws Exception {
		return this.auth.attemptLogin(username, password).getResponse().getStatus();
	}

	private void changePassword(Cookie cookie, String current, String next, int expected) throws Exception {
		this.mvc
			.perform(post("/api/v1/auth/change-password").cookie(cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(current == null ? "{\"password\":\"%s\"}".formatted(next)
							: "{\"currentPassword\":\"%s\",\"password\":\"%s\"}".formatted(current, next)))
			.andExpect(status().is(expected));
	}

	// --- what a username and password may look like ----------------------------------

	@Test
	void aPasswordUnderEightCharactersIsRejectedWithAFieldError() throws Exception {
		this.mvc
			.perform(post("/api/v1/users").cookie(this.admin)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"shorty\",\"password\":\"abc1234\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password").value("must be at least 8 characters"));
	}

	@Test
	void aUsernameWithSpacesOrSymbolsIsRejected() throws Exception {
		for (String bad : new String[] { "has space", "a", "semi;colon", "ünicode" }) {
			this.mvc
				.perform(post("/api/v1/users").cookie(this.admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"%s\",\"password\":\"long-enough-1\"}".formatted(bad)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.username").exists());
		}
	}

	@Test
	void usernamesAreCaseInsensitive() throws Exception {
		this.auth.createUser(this.admin, "Mixed.Case", "long-enough-1", null);

		assertThat(loginStatus("mixed.case", "long-enough-1")).isEqualTo(200);
		assertThat(loginStatus("MIXED.CASE", "long-enough-1")).isEqualTo(200);
		this.mvc
			.perform(post("/api/v1/users").cookie(this.admin)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"mixed.CASE\",\"password\":\"long-enough-1\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void anAdminCanCreateAnotherAdmin() throws Exception {
		this.auth.createUser(this.admin, "second.admin", "long-enough-1", "ADMIN");
		Cookie second = this.auth.loginAs("second.admin", "long-enough-1");

		this.mvc.perform(get("/api/v1/users").cookie(second)).andExpect(status().isOk());
	}

	// --- changing your own password ---------------------------------------------------

	@Test
	void aVoluntaryChangeNeedsTheCurrentPassword() throws Exception {
		Cookie member = this.auth.memberSession(this.admin, "changer");

		changePassword(member, null, "another-password", 403);
		changePassword(member, "not-my-password", "another-password", 403);
		changePassword(member, "my-own-password", "another-password", 200);

		assertThat(loginStatus("changer", "my-own-password")).isEqualTo(401);
		assertThat(loginStatus("changer", "another-password")).isEqualTo(200);
	}

	@Test
	void theForcedFirstChangeNeedsNoCurrentPassword() throws Exception {
		this.auth.createUser(this.admin, "newcomer", "temporary-pass", null);
		Cookie session = this.auth.loginAs("newcomer", "temporary-pass");

		changePassword(session, null, "chosen-by-me-1", 200);
	}

	// --- an admin resetting someone's password ----------------------------------------

	@Test
	void anAdminResetLocksOutTheOldPasswordAndSessionsAndForcesAChange() throws Exception {
		long id = this.auth.createUser(this.admin, "forgetful", "temporary-pass", null);
		Cookie oldSession = this.auth.loginAs("forgetful", "temporary-pass");
		changePassword(oldSession, null, "the-one-i-forgot", 200);

		this.mvc
			.perform(post("/api/v1/users/" + id + "/reset-password").cookie(this.admin)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"password\":\"fresh-temporary-1\"}"))
			.andExpect(status().isNoContent());

		assertThat(loginStatus("forgetful", "the-one-i-forgot")).isEqualTo(401);
		this.mvc.perform(get("/api/v1/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
		this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"forgetful\",\"password\":\"fresh-temporary-1\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	void aMemberCannotResetAnyonesPassword() throws Exception {
		Cookie member = this.auth.memberSession(this.admin, "nosy");

		this.mvc
			.perform(post("/api/v1/users/1/reset-password").cookie(member)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"password\":\"takeover-attempt\"}"))
			.andExpect(status().isForbidden());
		assertThat(loginStatus("admin", "changeme")).isEqualTo(200);
	}

	@Test
	void resettingAnUnknownAccountIsNotFoundAndATooShortPasswordIsRejected() throws Exception {
		this.mvc
			.perform(post("/api/v1/users/999999/reset-password").cookie(this.admin)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"password\":\"fresh-temporary-1\"}"))
			.andExpect(status().isNotFound());
		this.mvc
			.perform(post("/api/v1/users/1/reset-password").cookie(this.admin)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"password\":\"short\"}"))
			.andExpect(status().isBadRequest());
	}

	// --- sessions -----------------------------------------------------------------------

	@Test
	void aSessionStopsWorkingAfterThirtyDays() throws Exception {
		Cookie member = this.auth.memberSession(this.admin, "longgone");
		this.mvc.perform(get("/api/v1/auth/me").cookie(member)).andExpect(status().isOk());

		this.clock.set(START.plusDays(29));
		this.mvc.perform(get("/api/v1/auth/me").cookie(member)).andExpect(status().isOk());

		this.clock.set(START.plusDays(31));
		this.mvc.perform(get("/api/v1/auth/me").cookie(member)).andExpect(status().isUnauthorized());
	}

	@Test
	void loggingInPurgesExpiredSessions() throws Exception {
		this.sessions.save(new UserSession("stale-token", 1L, START.minusDays(40), START.minusDays(10)));

		this.auth.login();

		assertThat(this.sessions.findById("stale-token")).isEmpty();
	}

	@Test
	void loggingOutEndsOnlyThatSession() throws Exception {
		this.auth.createUser(this.admin, "twodevices", "temporary-pass", null);
		Cookie phone = this.auth.loginAs("twodevices", "temporary-pass");
		Cookie laptop = this.auth.loginAs("twodevices", "temporary-pass");

		this.mvc.perform(post("/api/v1/auth/logout").cookie(phone)).andExpect(status().isNoContent());

		this.mvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isOk());
	}

	@Test
	void aGarbageCookieIsJustNoSession() throws Exception {
		this.mvc.perform(get("/api/v1/tasks").cookie(new Cookie(AuthFilter.SESSION_COOKIE, "not-a-real-token")))
			.andExpect(status().isUnauthorized());
	}

	// --- guessing -----------------------------------------------------------------------

	@Test
	void fiveWrongPasswordsLockTheAccountEvenAgainstTheRightOne() throws Exception {
		this.auth.createUser(this.admin, "guessed", "correct-horse-1", null);
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			assertThat(loginStatus("guessed", "wrong-guess-" + i)).isEqualTo(401);
		}

		assertThat(loginStatus("guessed", "correct-horse-1")).isEqualTo(429);

		this.clock.set(START.plusSeconds(61));
		assertThat(loginStatus("guessed", "correct-horse-1")).isEqualTo(200);
	}

	@Test
	void aSuccessfulLoginResetsTheFailureCount() throws Exception {
		this.auth.createUser(this.admin, "clumsy", "correct-horse-1", null);
		for (int round = 0; round < 3; round++) {
			for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
				assertThat(loginStatus("clumsy", "typo-" + i)).isEqualTo(401);
			}
			assertThat(loginStatus("clumsy", "correct-horse-1")).isEqualTo(200);
		}
	}

	@Test
	void lockingOneAccountLeavesOthersUsable() throws Exception {
		this.auth.createUser(this.admin, "victim", "correct-horse-1", null);
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			loginStatus("victim", "wrong-guess-" + i);
		}

		assertThat(loginStatus("victim", "correct-horse-1")).isEqualTo(429);
		assertThat(loginStatus("admin", "changeme")).isEqualTo(200);
	}

	@Test
	void anAdminResetClearsAlockout() throws Exception {
		long id = this.auth.createUser(this.admin, "lockedout", "correct-horse-1", null);
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			loginStatus("lockedout", "wrong-guess-" + i);
		}
		assertThat(loginStatus("lockedout", "correct-horse-1")).isEqualTo(429);

		this.mvc.perform(post("/api/v1/users/" + id + "/reset-password").cookie(this.admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"fresh-temporary-1\"}"));

		assertThat(loginStatus("lockedout", "fresh-temporary-1")).isEqualTo(200);
	}

}
