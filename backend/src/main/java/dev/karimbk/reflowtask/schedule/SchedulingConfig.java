package dev.karimbk.reflowtask.schedule;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Everything the planner needs to know about when work may happen. A plain value type with
 * no persistence attached, so the algorithm can be tested without a database.
 *
 * @param workingHours when work may be placed. A weekday absent from this list is not a
 * working day, which makes "I don't work Fridays" an absence rather than a special case.
 * @param blockedPeriods recurring unavailable time inside working hours, such as lunch
 * @param horizonDays how far ahead to plan, counting today
 * @param minChunkMinutes smallest piece a split task may be broken into, so long work does
 * not shatter into useless fragments
 */
public record SchedulingConfig(List<DailyWindow> workingHours, List<DailyWindow> blockedPeriods, int horizonDays,
		int minChunkMinutes) {

	public SchedulingConfig {
		if (horizonDays < 1) {
			throw new IllegalArgumentException("horizonDays must be at least 1, was " + horizonDays);
		}
		if (minChunkMinutes < 1) {
			throw new IllegalArgumentException("minChunkMinutes must be at least 1, was " + minChunkMinutes);
		}
		workingHours = List.copyOf(workingHours);
		blockedPeriods = List.copyOf(blockedPeriods);
	}

	List<DailyWindow> workingHoursOn(DayOfWeek day) {
		return this.workingHours.stream().filter((window) -> window.day() == day).toList();
	}

	List<DailyWindow> blockedPeriodsOn(DayOfWeek day) {
		return this.blockedPeriods.stream().filter((window) -> window.day() == day).toList();
	}

}
