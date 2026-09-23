package dev.karimbk.reflowtask.user;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import dev.karimbk.reflowtask.common.ConflictException;
import dev.karimbk.reflowtask.common.ForbiddenException;
import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.common.TooManyAttemptsException;
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

	/** The bcrypt hash V4__household_users.sql seeds for the placeholder password. */
	static final String SEEDED_ADMIN_HASH = "$2b$10$t2YUtyzUEzW1Zjqo7I5qK.dZtl9WPry/gkE76dT93W8d1njJ.ovIi";

	private static final int SESSION_DAYS = 30;

	private final UserRepository users;

	private final UserSessionRepository sessions;

	private final LoginThrottle throttle;

	private final Clock clock;

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private final SecureRandom random = new SecureRandom();

	UserService(UserRepository users, UserSessionRepository sessions, LoginThrottle throttle, Clock clock) {
		this.users = users;
		this.sessions = sessions;
		this.throttle = throttle;
		this.clock = clock;
	}

	/** Usernames are compared case-insensitively by storing them trimmed and lower-cased. */
	static String normalize(String username) {
		return username.strip().toLowerCase(Locale.ROOT);
	}

	/** A logged-in user together with the cookie the response should carry. */
	public record LoginResult(User user, Cookie cookie) {
	}

	@Transactional
	public LoginResult login(String rawUsername, String password) {
		String username = normalize(rawUsername);
		if (this.throttle.isLocked(username)) {
			throw new TooManyAttemptsException("Too many failed attempts. Try again in a minute.");
		}
		User user = this.users.findByUsername(username)
			.filter((candidate) -> this.passwordEncoder.matches(password, candidate.getPasswordHash()))
			.orElse(null);
		if (user == null) {
			this.throttle.recordFailure(username);
			throw new UnauthorizedException("Wrong username or password.");
		}
		this.throttle.recordSuccess(username);
		// Housekeeping on the only path that creates sessions, so no separate job is needed.
		this.sessions.deleteByExpiresAtBefore(LocalDateTime.now(this.clock));
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
	 * A forced first change (after an admin created or reset the account) needs no current
	 * password, the person just typed the temporary one to log in. A voluntary change does, so a
	 * borrowed, still-logged-in browser cannot quietly take the account over.
	 *
	 * The {@code user} passed in was resolved by {@link AuthFilter} in its own, already-closed
	 * transaction, so it arrives detached - mutating its fields alone would never be flushed.
	 * Saving it explicitly is what actually persists the change.
	 */
	@Transactional
	public void changePassword(User user, String currentPassword, String newPassword) {
		if (!user.isMustChangePassword()
				&& (currentPassword == null || !this.passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
			throw new ForbiddenException("The current password is wrong.");
		}
		user.setPasswordHash(this.passwordEncoder.encode(newPassword));
		user.setMustChangePassword(false);
		this.users.save(user);
	}

	@Transactional(readOnly = true)
	public List<User> listAll() {
		return this.users.findAllByOrderByUsername();
	}

	/** Admin-created account, forced to change its temporary password before anything else. */
	@Transactional
	public User createMember(String username, String password, Role role) {
		String normalized = normalize(username);
		if (this.users.existsByUsername(normalized)) {
			throw new ConflictException("That username is already taken.");
		}
		return this.users
			.save(new User(normalized, this.passwordEncoder.encode(password), (role != null) ? role : Role.MEMBER,
					true, LocalDateTime.now(this.clock)));
	}

	/**
	 * An admin sets a new temporary password for someone who is locked out. The person must choose
	 * their own at next login, and every session they had is ended.
	 */
	@Transactional
	public void resetPassword(long targetId, String temporaryPassword) {
		User target = this.users.findById(targetId).orElseThrow(() -> new NotFoundException("User", targetId));
		applyTemporaryPassword(target, temporaryPassword);
	}

	/** The same reset for the device owner, who names the account instead of being logged in. */
	@Transactional
	public void resetPasswordOf(String username, String temporaryPassword) {
		User target = this.users.findByUsername(normalize(username))
			.orElseThrow(() -> new NotFoundException("User", username));
		applyTemporaryPassword(target, temporaryPassword);
	}

	private void applyTemporaryPassword(User target, String temporaryPassword) {
		target.setPasswordHash(this.passwordEncoder.encode(temporaryPassword));
		target.setMustChangePassword(true);
		this.users.save(target);
		this.sessions.deleteByUserId(target.getId());
		this.throttle.recordSuccess(target.getUsername());
	}

	/**
	 * Replaces the seeded placeholder password with one the installer chose in their environment,
	 * so a fresh install is never reachable with a password every copy of this project shares.
	 * Does nothing once the placeholder has been replaced by anyone.
	 */
	@Transactional
	public boolean replaceSeededAdminPassword(String password) {
		return this.users.findById(1L)
			.filter((admin) -> SEEDED_ADMIN_HASH.equals(admin.getPasswordHash()))
			.map((admin) -> {
				admin.setPasswordHash(this.passwordEncoder.encode(password));
				admin.setMustChangePassword(false);
				this.users.save(admin);
				return true;
			})
			.orElse(false);
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
		// Their tasks, blocks, hours and history go with them: the database cascades every
		// user-owned table, so nothing of a removed member is left behind.
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
		cookie.setMaxAge((int) Duration.ofDays(SESSION_DAYS).toSeconds());
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
