package dev.karimbk.reflowtask.schedule;

/** Why a replan happened, so the history explains itself. */
public enum RescheduleTrigger {

	/** A task was created, edited or completed. */
	TASK_CHANGED,

	/** The periodic job found missed work. */
	SCHEDULED_JOB,

	/** The user asked for a replan explicitly. */
	MANUAL,

	/**
	 * Working hours, blocked time or planning settings changed. A schedule laid out against
	 * the old hours is wrong the moment they change, so it is rebuilt immediately.
	 */
	CONFIG_CHANGED

}
