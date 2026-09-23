package dev.karimbk.reflowtask.user;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import dev.karimbk.reflowtask.common.ConflictException;
import dev.karimbk.reflowtask.common.ForbiddenException;
import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.common.UnauthorizedException;
import jakarta.servlet.http.Cookie;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Household accounts and sessions.
 *
 * A hand-rolled session cookie, not Spring Security: this never needs to be internet-facing, so
 * a full filter chain is ceremony for "is there a valid cookie, whose user is it". The one thing
 * kept from a real security library is password hashing.
 */
@Service
public class UserService {

	private static final int SESSION_DAYS = 30;

	private final UserRepository users;

	private final UserSessionRepository sessions;

	private final Clock clock;

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private final SecureRandom random = new SecureRandom();

	UserService(UserRepository users, UserSessionRepository sessions, Clock clock) {
		this.users = users;
		this.sessions = sessions;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public boolean needsBootstrap() {
		return this.users.count() == 0;
	}

	/** A logged-in user together with the cookie the response should carry. */
	public record LoginResult(User user, Cookie cookie) {
	}

	/** Creates the first account, always an admin. Refused once any account exists. */
	@Transactional
	public LoginResult bootstrap(String username, String password) {
		if (!needsBootstrap()) {
			throw new ConflictException("Setup has already run.");
		}
		User user = this.users
			.save(new User(username, this.passwordEncoder.encode(password), Role.ADMIN, false,
					LocalDateTime.now(this.clock)));
		return new LoginResult(user, startSession(user));
	}

	@Transactional
	public LoginResult login(String username, String password) {
		User user = this.users.findByUsername(username)
			.filter((candidate) -> this.passwordEncoder.matches(password, candidate.getPasswordHash()))
			.orElseThrow(() -> new UnauthorizedException("Wrong username or password."));
		return new LoginResult(user, startSession(user));
	}

	@Transactional
	public Cookie logout(String token) {
		if (token != null) {
			this.sessions.deleteById(token);
		}
		return clearSessionCookie();
	}

	/**
	 * The {@code user} passed in was resolved by {@link AuthFilter} in its own, already-closed
	 * transaction, so it arrives detached - mutating its fields alone would never be flushed.
	 * Saving it explicitly is what actually persists the change.
	 */
	@Transactional
	public void changePassword(User user, String newPassword) {
		user.setPasswordHash(this.passwordEncoder.encode(newPassword));
		user.setMustChangePassword(false);
		this.users.save(user);
	}

	@Transactional(readOnly = true)
	public List<User> listAll() {
		return this.users.findAllByOrderByUsername();
	}

	/** Admin-created member, forced to change their placeholder password before anything else. */
	@Transactional
	public User createMember(String username, String password) {
		if (this.users.existsByUsername(username)) {
			throw new ConflictException("That username is already taken.");
		}
		return this.users
			.save(new User(username, this.passwordEncoder.encode(password), Role.MEMBER, true,
					LocalDateTime.now(this.clock)));
	}

	@Transactional
	public void deleteMember(User actingAdmin, long targetId) {
		if (actingAdmin.getId() == targetId) {
			throw new ConflictException("You cannot delete your own account.");
		}
		User target = this.users.findById(targetId).orElseThrow(() -> new NotFoundException("User", targetId));
		if (target.isAdmin() && this.users.countByRole(Role.ADMIN) <= 1) {
			throw new ConflictException("The last admin account cannot be deleted.");
		}
		this.sessions.deleteByUserId(targetId);
		this.users.delete(target);
	}

	public void requireAdmin(User user) {
		if (!user.isAdmin()) {
			throw new ForbiddenException("Only an admin can do that.");
		}
	}

	private Cookie startSession(User user) {
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
		LocalDateTime now = LocalDateTime.now(this.clock);
		this.sessions.save(new UserSession(token, user.getId(), now, now.plusDays(SESSION_DAYS)));
		Cookie cookie = new Cookie(AuthFilter.SESSION_COOKIE, token);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		cookie.setMaxAge((int) java.time.Duration.ofDays(SESSION_DAYS).toSeconds());
		// No Secure flag: this deployment is served over plain HTTP (see Caddyfile), and a
		// Secure cookie is silently never sent over plain HTTP - that would break login outright.
		// SameSite=Lax alone already keeps the cookie off a cross-site state-changing request,
		// which is what CSRF protection exists to prevent for a same-origin SPA.
		cookie.setAttribute("SameSite", "Lax");
		return cookie;
	}

	private Cookie clearSessionCookie() {
		Cookie cookie = new Cookie(AuthFilter.SESSION_COOKIE, "");
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		cookie.setMaxAge(0);
		cookie.setAttribute("SameSite", "Lax");
		return cookie;
	}

	private byte[] randomBytes() {
		byte[] bytes = new byte[32];
		this.random.nextBytes(bytes);
		return bytes;
	}

}
