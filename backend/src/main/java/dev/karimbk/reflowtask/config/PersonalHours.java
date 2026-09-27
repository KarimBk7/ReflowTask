package dev.karimbk.reflowtask.config;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * When personal tasks may be planned on a given weekday, such as evenings and weekends. Shaped like
 * {@link WorkingHours} and keyed the same way; a user with no rows plans personal tasks in working
 * hours.
 */
@Entity
@Table(name = "personal_hours")
@IdClass(WorkingHoursId.class)
public class PersonalHours {

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

	protected PersonalHours() {
		// for JPA
	}

	public PersonalHours(long userId, DayOfWeek day, LocalTime startTime, LocalTime endTime) {
		this.userId = userId;
		this.dayOfWeek = (short) day.getValue();
		this.startTime = startTime;
		this.endTime = endTime;
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
