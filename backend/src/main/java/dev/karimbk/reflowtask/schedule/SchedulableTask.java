package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;

import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.TimeProfile;

/**
 * What the planner needs from a task, and nothing more. Keeping this separate from the JPA
 * entity is what lets the algorithm be exercised without a database.
 *
 * @param minutesToPlace effort still needing a slot. Minutes already covered by pinned
 * blocks are subtracted before planning, so a partly pinned task only gets its remainder
 * scheduled.
 * @param notBefore nothing of the task is placed before this; null for any time
 * @param profile which hours it may be placed in
 */
public record SchedulableTask(long id, int minutesToPlace, LocalDateTime deadline, Priority priority,
		LocalDateTime notBefore, TimeProfile profile) {

	public SchedulableTask(long id, int minutesToPlace, LocalDateTime deadline, Priority priority) {
		this(id, minutesToPlace, deadline, priority, null);
	}

	public SchedulableTask(long id, int minutesToPlace, LocalDateTime deadline, Priority priority,
			LocalDateTime notBefore) {
		this(id, minutesToPlace, deadline, priority, notBefore, TimeProfile.WORK);
	}

}
