package dev.karimbk.reflowtask.config;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * When a user works on a given weekday. A weekday with no row is not a working day, so
 * "I don't work Fridays" is a deletion rather than a flag.
 *
 * The day is stored as the ISO number (1 = Monday) rather than an enum ordinal, because
 * ordinals are zero-based and would silently disagree with the SQL CHECK constraint.
 */
@Entity
@Table(name = "working_hours")
@IdClass(WorkingHoursId.class)
public class WorkingHours {

	@jakarta.persistence.Id
	@Column(nullable = false)
	private long userId;

	@jakarta.persistence.Id
	@Column(name = "day_of_week", nullable = false)
	private short dayOfWeek;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	protected WorkingHours() {
		// for JPA
	}

	public WorkingHours(long userId, DayOfWeek day, LocalTime startTime, LocalTime endTime) {
		this.userId = userId;
		this.dayOfWeek = (short) day.getValue();
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public long getUserId() {
		return this.userId;
	}

	public DayOfWeek getDay() {
		return DayOfWeek.of(this.dayOfWeek);
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

}
