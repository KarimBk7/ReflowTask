package dev.karimbk.reflowtask.schedule;

import java.time.DayOfWeek;
import java.util.List;

import dev.karimbk.reflowtask.task.TimeProfile;

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
 * @param bufferMinutes minutes kept free between scheduled tasks and on both sides of fixed
 * blocks, so a day is not planned wall to wall. 0 places work back to back.
 * @param freezeMinutes planned blocks starting this soon are kept where they are by a replan. Not
 * used by the planner itself: it decides which existing blocks become obstacles. 0 is off.
 * @param personalHours when personal tasks may be placed. Empty means they use working hours, so a
 * personal task is never left unplaced just because nobody set personal time up.
 */
public record SchedulingConfig(List<DailyWindow> workingHours, List<DailyWindow> blockedPeriods, int horizonDays,
		int minChunkMinutes, int bufferMinutes, int freezeMinutes, List<DailyWindow> personalHours) {

	public SchedulingConfig {
		if (horizonDays < 1) {
			throw new IllegalArgumentException("horizonDays must be at least 1, was " + horizonDays);
		}
		if (minChunkMinutes < 1) {
			throw new IllegalArgumentException("minChunkMinutes must be at least 1, was " + minChunkMinutes);
		}
		if (bufferMinutes < 0) {
			throw new IllegalArgumentException("bufferMinutes cannot be negative, was " + bufferMinutes);
		}
		if (freezeMinutes < 0) {
			throw new IllegalArgumentException("freezeMinutes cannot be negative, was " + freezeMinutes);
		}
		workingHours = List.copyOf(workingHours);
		blockedPeriods = List.copyOf(blockedPeriods);
		personalHours = List.copyOf(personalHours);
	}

	public SchedulingConfig(List<DailyWindow> workingHours, List<DailyWindow> blockedPeriods, int horizonDays,
			int minChunkMinutes, int bufferMinutes, int freezeMinutes) {
		this(workingHours, blockedPeriods, horizonDays, minChunkMinutes, bufferMinutes, freezeMinutes, List.of());
	}

	public SchedulingConfig(List<DailyWindow> workingHours, List<DailyWindow> blockedPeriods, int horizonDays,
			int minChunkMinutes, int bufferMinutes) {
		this(workingHours, blockedPeriods, horizonDays, minChunkMinutes, bufferMinutes, 0);
	}

	/** Without a buffer: work is placed back to back, exactly as before buffers existed. */
	public SchedulingConfig(List<DailyWindow> workingHours, List<DailyWindow> blockedPeriods, int horizonDays,
			int minChunkMinutes) {
		this(workingHours, blockedPeriods, horizonDays, minChunkMinutes, 0);
	}

	List<DailyWindow> workingHoursOn(DayOfWeek day) {
		return this.workingHours.stream().filter((window) -> window.day() == day).toList();
	}

	/** The windows a task of this profile may be placed in. */
	public List<DailyWindow> hoursFor(TimeProfile profile) {
		return (profile == TimeProfile.PERSONAL && !this.personalHours.isEmpty()) ? this.personalHours
				: this.workingHours;
	}

	List<DailyWindow> hoursOn(TimeProfile profile, DayOfWeek day) {
		return hoursFor(profile).stream().filter((window) -> window.day() == day).toList();
	}

	List<DailyWindow> blockedPeriodsOn(DayOfWeek day) {
		return this.blockedPeriods.stream().filter((window) -> window.day() == day).toList();
	}

}
