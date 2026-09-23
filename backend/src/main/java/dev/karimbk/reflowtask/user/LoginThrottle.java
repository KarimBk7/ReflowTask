package dev.karimbk.reflowtask.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Slows down password guessing: after {@value #MAX_FAILURES} wrong passwords in a row for one
 * username, that username is locked for {@link #LOCK} regardless of the password tried next.
 *
 * Kept in memory on purpose. A restart clears it, which is fine for a household server, and it
 * avoids a table and a cleanup job. Keyed by username rather than address: behind a reverse
 * proxy every request comes from the proxy, so an address would lock everyone at once.
 * ponytail: per-process map; move to the database if this ever runs as several instances.
 */
@Component
class LoginThrottle {

	static final int MAX_FAILURES = 5;

	static final Duration LOCK = Duration.ofMinutes(1);

	private record Attempts(int failures, Instant lockedUntil) {
	}

	private final Map<String, Attempts> byUsername = new ConcurrentHashMap<>();

	private final Clock clock;

	LoginThrottle(Clock clock) {
		this.clock = clock;
	}

	boolean isLocked(String username) {
		Attempts attempts = this.byUsername.get(username);
		return attempts != null && attempts.lockedUntil() != null && attempts.lockedUntil().isAfter(now());
	}

	void recordFailure(String username) {
		this.byUsername.merge(username, new Attempts(1, null), (old, ignored) -> {
			int failures = (old.lockedUntil() != null && !old.lockedUntil().isAfter(now())) ? 1 : old.failures() + 1;
			return new Attempts(failures, failures >= MAX_FAILURES ? now().plus(LOCK) : null);
		});
	}

	void recordSuccess(String username) {
		this.byUsername.remove(username);
	}

	private Instant now() {
		return this.clock.instant();
	}

}
