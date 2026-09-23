package dev.karimbk.reflowtask.user;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Every endpoint requires a session now. V4__household_users.sql seeds one admin account
 * ('admin' / 'changeme') as part of the schema, outside any test's own transaction, so it is
 * present for every test regardless of rollback. Logging in as that seeded admin, rather than
 * bootstrapping a new one, is what lets every existing test keep using one shared session.
 *
 * A plain helper rather than a Spring bean: it only makes sense where a {@link MockMvc} exists,
 * and a handful of API tests (e.g. {@code ReflowTests}) drive the service layer directly with
 * no web layer in their context at all.
 */
public class AuthTestSupport {

	private final MockMvc mvc;

	public AuthTestSupport(MockMvc mvc) {
		this.mvc = mvc;
	}

	public Cookie login() throws Exception {
		return loginAs("admin", "changeme");
	}

	public Cookie loginAs(String username, String password) throws Exception {
		return attemptLogin(username, password).getResponse().getCookie(AuthFilter.SESSION_COOKIE);
	}

	public MvcResult attemptLogin(String username, String password) throws Exception {
		return this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
			.andReturn();
	}

	/** Creates a member as the given admin and returns its id. */
	public long createUser(Cookie admin, String username, String password, String role) throws Exception {
		String body = this.mvc
			.perform(post("/api/v1/users").cookie(admin)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"%s\",\"password\":\"%s\",\"role\":%s}".formatted(username, password,
							role == null ? "null" : "\"" + role + "\"")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	/**
	 * A member ready to use the board: created by the admin, then logged in and past the forced
	 * first password change, so its session works for every ordinary endpoint.
	 */
	public Cookie memberSession(Cookie admin, String username) throws Exception {
		createUser(admin, username, "temporary-pass", null);
		Cookie cookie = loginAs(username, "temporary-pass");
		this.mvc.perform(post("/api/v1/auth/change-password").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"my-own-password\"}"));
		return cookie;
	}

}
