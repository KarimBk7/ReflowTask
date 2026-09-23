package dev.karimbk.reflowtask.user;

import java.time.Clock;
import java.time.LocalDateTime;

import dev.karimbk.reflowtask.common.ConflictException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Household login: the seeded admin, bootstrap being a one-time affair, member accounts an
 * admin creates and deletes, and the guardrails around deleting the last admin or yourself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	/** Matches V4__household_users.sql's seeded hash for 'changeme'. */
	private static final String SEEDED_ADMIN_PASSWORD_HASH =
			"$2b$10$t2YUtyzUEzW1Zjqo7I5qK.dZtl9WPry/gkE76dT93W8d1njJ.ovIi";

	private Cookie adminCookie() throws Exception {
		return new AuthTestSupport(this.mvc).login();
	}

	@Test
	void bootstrapIsRefusedOnceTheSeededAdminAlreadyExists() throws Exception {
		this.mvc
			.perform(post("/api/v1/auth/bootstrap").contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"someone","password":"whatever1"}"""))
			.andExpect(status().isConflict());
	}

	@Test
	void statusReportsNoBootstrapNeededOnceAUserExists() throws Exception {
		this.mvc.perform(get("/api/v1/auth/status")).andExpect(jsonPath("$.needsBootstrap").value(false));
	}

	@Test
	void loginWithTheWrongPasswordIsUnauthorized() throws Exception {
		this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"admin","password":"wrong"}"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithTheSeededPasswordSucceedsAndReportsMustChangePassword() throws Exception {
		this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"admin","password":"changeme"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	void meWithNoSessionIsUnauthorized() throws Exception {
		this.mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void meReflectsTheLoggedInUser() throws Exception {
		this.mvc.perform(get("/api/v1/auth/me").cookie(adminCookie())).andExpect(jsonPath("$.username").value("admin"));
	}

	/**
	 * Regression: {@code @Transactional} on this test class wraps the whole method in one
	 * Hibernate session, so the user entity {@link CurrentUserArgumentResolver} resolves stays
	 * managed across every MockMvc call in an ordinary test - masking a real bug where
	 * {@code UserService.changePassword} mutated that entity without saving it. In production,
	 * with no test transaction wrapping the request, {@link AuthFilter} loads the user in its
	 * own already-closed transaction, so it arrives detached and an unsaved mutation is silently
	 * lost. Suspending the test transaction here reproduces that: each request below gets its
	 * own real transaction, exactly like a live request.
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void changingThePasswordActuallyPersistsAcrossSeparateRequests() throws Exception {
		Cookie cookie = adminCookie();
		try {
			this.mvc
				.perform(post("/api/v1/auth/change-password").cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"password":"a-new-password"}"""))
				.andExpect(jsonPath("$.mustChangePassword").value(false));

			// A fresh request, resolving the user through AuthFilter's own separate transaction -
			// this is what a detached, unsaved mutation would fail in front of.
			this.mvc
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"admin","password":"a-new-password"}"""))
				.andExpect(status().isOk());
		}
		finally {
			// Leave the seeded admin exactly as every other test expects it: this ran with the
			// test transaction suspended, so it is a real, committed change to the shared database.
			this.users.findByUsername("admin").ifPresent((admin) -> {
				admin.setPasswordHash(SEEDED_ADMIN_PASSWORD_HASH);
				admin.setMustChangePassword(true);
				this.users.save(admin);
			});
		}
	}

	@Test
	void changingThePasswordClearsMustChangePassword() throws Exception {
		Cookie cookie = adminCookie();

		this.mvc
			.perform(post("/api/v1/auth/change-password").cookie(cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"password":"a-new-password"}"""))
			.andExpect(jsonPath("$.mustChangePassword").value(false));

		this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"admin","password":"a-new-password"}"""))
			.andExpect(status().isOk());
	}

	@Test
	void logoutInvalidatesTheSession() throws Exception {
		Cookie cookie = adminCookie();

		this.mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
		this.mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
	}

	@Test
	void anAdminCanListAndCreateMembers() throws Exception {
		Cookie admin = adminCookie();

		this.mvc
			.perform(post("/api/v1/users").cookie(admin)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"kid","password":"changeme1"}"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.mustChangePassword").value(true));

		this.mvc.perform(get("/api/v1/users").cookie(admin))
			.andExpect(jsonPath("$[?(@.username == 'kid')]").exists())
			.andExpect(jsonPath("$[?(@.username == 'admin')]").exists());
	}

	@Test
	void aMemberCannotReachAdminEndpoints() throws Exception {
		Cookie admin = adminCookie();
		this.mvc.perform(post("/api/v1/users").cookie(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"kid","password":"changeme1"}"""));
		Cookie member = this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"kid","password":"changeme1"}"""))
			.andReturn()
			.getResponse()
			.getCookie(AuthFilter.SESSION_COOKIE);

		this.mvc.perform(get("/api/v1/users").cookie(member)).andExpect(status().isForbidden());
	}

	@Test
	void anAdminCannotDeleteTheirOwnAccount() throws Exception {
		Cookie admin = adminCookie();

		this.mvc.perform(delete("/api/v1/users/1").cookie(admin)).andExpect(status().isConflict());
	}

	/**
	 * With two admins, deleting one through the API is fine (not self, not the last admin).
	 */
	@Test
	void anAdminCanDeleteAnotherAdmin() throws Exception {
		Cookie admin = adminCookie();
		User secondAdmin = this.users
			.save(new User("second-admin", "irrelevant-hash", Role.ADMIN, false, LocalDateTime.now()));

		this.mvc.perform(delete("/api/v1/users/" + secondAdmin.getId()).cookie(admin))
			.andExpect(status().isNoContent());
	}

	/**
	 * With only one admin ever calling the endpoint, they are always deleting themselves, which
	 * the self-delete guard already catches - so the last-admin guard is tested directly against
	 * the service, the only way to exercise "not self, but the last admin" at all.
	 */
	@Test
	void theServiceRefusesToDeleteTheLastAdmin() {
		User onlyAdmin = this.users.findByUsername("admin").orElseThrow();
		User otherActor = this.users
			.save(new User("temp-actor", "irrelevant-hash", Role.ADMIN, false, LocalDateTime.now()));
		this.users.delete(otherActor); // Exists just long enough to differ from onlyAdmin's id.

		assertThatThrownBy(
				() -> new UserService(this.users, null, Clock.systemDefaultZone()).deleteMember(otherActor,
						onlyAdmin.getId()))
			.isInstanceOf(ConflictException.class);
	}

}
