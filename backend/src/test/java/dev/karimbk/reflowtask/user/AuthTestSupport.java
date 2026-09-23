package dev.karimbk.reflowtask.user;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
		return this.mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"admin","password":"changeme"}"""))
			.andReturn()
			.getResponse()
			.getCookie(AuthFilter.SESSION_COOKIE);
	}

}
