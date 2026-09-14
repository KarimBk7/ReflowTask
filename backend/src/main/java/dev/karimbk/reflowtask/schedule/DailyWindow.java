package dev.karimbk.reflowtask.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A recurring weekly time range. One type serves both working hours and blocked periods:
 * they differ only in whether the planner treats the window as capacity or as an obstacle.
 */
public record DailyWindow(DayOfWeek day, LocalTime start, LocalTime end) {

	public DailyWindow {
		if (!end.isAfter(start)) {
			throw new IllegalArgumentException("window must end after it starts: " + start + " to " + end);
		}
	}

	/** Materializes this recurring window onto a concrete date. */
	public TimeSlot on(LocalDate date) {
		return new TimeSlot(date.atTime(this.start), date.atTime(this.end));
	}

}
