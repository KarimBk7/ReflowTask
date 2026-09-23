package dev.karimbk.reflowtask.config;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Recurring time inside working hours that the scheduler must leave empty, such as lunch. */
@Entity
@Table(name = "blocked_period")
public class BlockedPeriod {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long userId;

	@Column(name = "day_of_week", nullable = false)
	private short dayOfWeek;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	private String label;

	protected BlockedPeriod() {
		// for JPA
	}

	public BlockedPeriod(long userId, DayOfWeek day, LocalTime startTime, LocalTime endTime, String label) {
		this.userId = userId;
		this.dayOfWeek = (short) day.getValue();
		this.startTime = startTime;
		this.endTime = endTime;
		this.label = label;
	}

	public Long getId() {
		return this.id;
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

	public String getLabel() {
		return this.label;
	}

}
