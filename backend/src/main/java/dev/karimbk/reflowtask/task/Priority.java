package dev.karimbk.reflowtask.task;

/**
 * Declared low-to-high so natural enum ordering matches "more urgent is greater".
 * The scheduler sorts priority descending, which reversed natural order gives for free.
 */
public enum Priority {
	LOW,
	MEDIUM,
	HIGH
}
