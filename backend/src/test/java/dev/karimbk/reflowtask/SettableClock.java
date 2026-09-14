package dev.karimbk.reflowtask;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A real Clock the tests can move, rather than a mock. Mocking {@link Clock#instant()} works
 * but reads badly for a value type, and a mock cannot be advanced mid-test the way the reflow
 * scenarios need.
 */
public class SettableClock extends Clock {

	private final ZoneId zone;

	private Instant instant;

	public SettableClock(LocalDateTime now, ZoneId zone) {
		this.zone = zone;
		this.instant = now.atZone(zone).toInstant();
	}

	public void set(LocalDateTime now) {
		this.instant = now.atZone(this.zone).toInstant();
	}

	@Override
	public ZoneId getZone() {
		return this.zone;
	}

	@Override
	public Clock withZone(ZoneId otherZone) {
		return new SettableClock(LocalDateTime.ofInstant(this.instant, otherZone), otherZone);
	}

	@Override
	public Instant instant() {
		return this.instant;
	}

}
