package dev.karimbk.reflowtask.task;

import java.time.LocalDateTime;

/** How often a task comes back. Each occurrence is due one step after the previous one. */
public enum Recurrence {

	/** Every working day: days without working hours are skipped by the scheduler. */
	DAILY,

	WEEKLY,

	BIWEEKLY,

	// ponytail: stepping month by month drifts a 31st deadline to the 30th, then stays there;
	// keep the original day of month on the series if that ever matters.
	MONTHLY;

	public LocalDateTime next(LocalDateTime from) {
		return switch (this) {
			case DAILY -> from.plusDays(1);
			case WEEKLY -> from.plusWeeks(1);
			case BIWEEKLY -> from.plusWeeks(2);
			case MONTHLY -> from.plusMonths(1);
		};
	}

}
