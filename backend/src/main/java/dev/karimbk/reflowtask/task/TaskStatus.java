package dev.karimbk.reflowtask.task;

/**
 * The only statuses a user sets. "Missed", "at risk" and "partially scheduled" are
 * deliberately absent: they are derived from the clock and the task's blocks, so
 * storing them would let them go stale.
 */
public enum TaskStatus {
	OPEN,
	IN_PROGRESS,
	DONE;

	/** Done tasks are invisible to the scheduler; everything else gets planned. */
	public boolean isSchedulable() {
		return this != DONE;
	}
}
