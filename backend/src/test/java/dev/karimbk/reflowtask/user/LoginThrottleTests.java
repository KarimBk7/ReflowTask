package dev.karimbk.reflowtask.user;

import java.time.LocalDateTime;
import java.time.ZoneId;

import dev.karimbk.reflowtask.SettableClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTests {

	private static final LocalDateTime START = LocalDateTime.of(2026, 9, 23, 12, 0);

	private final SettableClock clock = new SettableClock(START, ZoneId.of("UTC"));

	private final LoginThrottle throttle = new LoginThrottle(this.clock);

	private void fail(String username, int times) {
		for (int i = 0; i < times; i++) {
			this.throttle.recordFailure(username);
		}
	}

	@Test
	void fewerThanTheLimitDoesNotLock() {
		fail("a", LoginThrottle.MAX_FAILURES - 1);

		assertThat(this.throttle.isLocked("a")).isFalse();
	}

	@Test
	void reachingTheLimitLocksForOneMinute() {
		fail("a", LoginThrottle.MAX_FAILURES);

		assertThat(this.throttle.isLocked("a")).isTrue();
		this.clock.set(START.plusSeconds(59));
		assertThat(this.throttle.isLocked("a")).isTrue();
		this.clock.set(START.plusSeconds(61));
		assertThat(this.throttle.isLocked("a")).isFalse();
	}

	@Test
	void aFailureAfterTheLockExpiredStartsCountingAgainFromOne() {
		fail("a", LoginThrottle.MAX_FAILURES);
		this.clock.set(START.plusMinutes(2));

		fail("a", 1);

		assertThat(this.throttle.isLocked("a")).isFalse();
		fail("a", LoginThrottle.MAX_FAILURES - 2);
		assertThat(this.throttle.isLocked("a")).isFalse();
		fail("a", 1);
		assertThat(this.throttle.isLocked("a")).isTrue();
	}

	@Test
	void successClearsTheCount() {
		fail("a", LoginThrottle.MAX_FAILURES - 1);
		this.throttle.recordSuccess("a");

		fail("a", LoginThrottle.MAX_FAILURES - 1);

		assertThat(this.throttle.isLocked("a")).isFalse();
	}

	@Test
	void usernamesAreCountedSeparately() {
		fail("a", LoginThrottle.MAX_FAILURES);

		assertThat(this.throttle.isLocked("b")).isFalse();
	}

}
