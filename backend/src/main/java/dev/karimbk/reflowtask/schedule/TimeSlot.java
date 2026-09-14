package dev.karimbk.reflowtask.schedule;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A half-open interval [start, end). Used both for free capacity and for the obstacles
 * carved out of it.
 */
public record TimeSlot(LocalDateTime start, LocalDateTime end) {

	public TimeSlot {
		if (!end.isAfter(start)) {
			throw new IllegalArgumentException("slot must end after it starts: " + start + " to " + end);
		}
	}

	public long minutes() {
		return Duration.between(this.start, this.end).toMinutes();
	}

	public boolean overlaps(TimeSlot other) {
		return this.start.isBefore(other.end) && other.start.isBefore(this.end);
	}

	/** The part of this slot at or after {@code earliest}, or null if nothing remains. */
	public TimeSlot notBefore(LocalDateTime earliest) {
		if (!this.end.isAfter(earliest)) {
			return null;
		}
		return this.start.isBefore(earliest) ? new TimeSlot(earliest, this.end) : this;
	}

	public TimeSlot startingAt(LocalDateTime newStart) {
		return new TimeSlot(newStart, this.end);
	}

	public TimeSlot lasting(long durationMinutes) {
		return new TimeSlot(this.start, this.start.plusMinutes(durationMinutes));
	}

}
