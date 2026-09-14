package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;

import dev.karimbk.reflowtask.task.Priority;

/**
 * What the planner needs from a task, and nothing more. Keeping this separate from the JPA
 * entity is what lets the algorithm be exercised without a database.
 *
 * @param minutesToPlace effort still needing a slot. Minutes already covered by pinned
 * blocks are subtracted before planning, so a partly pinned task only gets its remainder
 * scheduled.
 */
public record SchedulableTask(long id, int minutesToPlace, LocalDateTime deadline, Priority priority) {
}
