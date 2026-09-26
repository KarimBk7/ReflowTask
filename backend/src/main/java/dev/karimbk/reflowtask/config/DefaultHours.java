package dev.karimbk.reflowtask.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import dev.karimbk.reflowtask.schedule.DailyWindow;

/**
 * The week someone plans against before they have saved hours of their own: Monday to Friday,
 * 09:00 to 18:00, as the first admin's migration seeded. One definition, because the setup screen
 * showing these hours while the scheduler planned against none was a real bug: a new member's
 * tasks were never placed until they saved hours.
 */
final class DefaultHours {

	static final List<DailyWindow> WEEKDAYS = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
			DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
		.stream()
		.map((day) -> new DailyWindow(day, LocalTime.of(9, 0), LocalTime.of(18, 0)))
		.toList();

	private DefaultHours() {
	}

	/** Applies only until the person first saves: after that, no working days is their choice. */
	static boolean apply(SchedulingSettings settings, boolean hasOwnHours) {
		return !settings.isOnboarded() && !hasOwnHours;
	}

}
