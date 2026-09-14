package dev.karimbk.reflowtask.schedule;

/** What happened to one task during a replan. */
public enum RescheduleItemKind {

	/** Its time passed without the task being completed. */
	MISSED,

	/** It was already scheduled and now sits somewhere else. */
	MOVED,

	/** It had no place in the schedule and now does. */
	PLACED,

	/** It needs time but the horizon has no room left. */
	UNPLACED

}
