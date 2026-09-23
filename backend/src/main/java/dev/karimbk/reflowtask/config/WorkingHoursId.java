package dev.karimbk.reflowtask.config;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link WorkingHours}: one row per user per weekday. */
public class WorkingHoursId implements Serializable {

	private long userId;

	private short dayOfWeek;

	protected WorkingHoursId() {
		// for JPA
	}

	public WorkingHoursId(long userId, short dayOfWeek) {
		this.userId = userId;
		this.dayOfWeek = dayOfWeek;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof WorkingHoursId other)) {
			return false;
		}
		return this.userId == other.userId && this.dayOfWeek == other.dayOfWeek;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.userId, this.dayOfWeek);
	}

}
