package dev.karimbk.reflowtask.user;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

/**
 * Resolves the session cookie into the logged-in user, once per request, and makes it available
 * to controllers as a request attribute. Does not itself reject unauthenticated requests - a
 * missing or invalid cookie just means no user is attached, and {@link CurrentUserArgumentResolver}
 * is what turns "no user" into a 401 for the endpoints that require one. Endpoints like
 * {@code /api/v1/auth/status}, {@code /api/v1/auth/bootstrap} and {@code /api/v1/auth/login}
 * never take a {@code @CurrentUser} parameter, so they work with no session at all.
 */
@Component
public class AuthFilter implements Filter {

	static final String CURRENT_USER_ATTRIBUTE = "dev.karimbk.reflowtask.currentUser";

	static final String SESSION_COOKIE = "rt_session";

	private final UserSessionRepository sessions;

	private final UserRepository users;

	private final Clock clock;

	AuthFilter(UserSessionRepository sessions, UserRepository users, Clock clock) {
		this.sessions = sessions;
		this.users = users;
		this.clock = clock;
	}

	@Override
	public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		findCookie(request).flatMap(this::resolveUser)
			.ifPresent((user) -> request.setAttribute(CURRENT_USER_ATTRIBUTE, user));
		chain.doFilter(req, (HttpServletResponse) res);
	}

	private Optional<String> findCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (SESSION_COOKIE.equals(cookie.getName())) {
				return Optional.of(cookie.getValue());
			}
		}
		return Optional.empty();
	}

	private Optional<User> resolveUser(String token) {
		LocalDateTime now = LocalDateTime.now(this.clock);
		return this.sessions.findById(token)
			.filter((session) -> !session.isExpired(now))
			.flatMap((session) -> this.users.findById(session.getUserId()));
	}

}
