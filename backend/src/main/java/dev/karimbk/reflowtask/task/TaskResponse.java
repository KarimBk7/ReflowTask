package dev.karimbk.reflowtask.task;

import java.time.LocalDateTime;

/**
 * @param scheduledMinutes how much of the estimate currently has a place on the schedule,
 * across all of the task's blocks. Less than {@code estimatedMinutes} means the horizon ran out
 * of room. Derived on every read rather than stored, so it cannot disagree with the schedule.
 * @param doneMinutes how much of it was marked done, part by part
 * @param atRisk some of the task's work is placed after its deadline
 */
public record TaskResponse(
		Long id,
		String title,
		String description,
		int estimatedMinutes,
		LocalDateTime deadline,
		boolean deadlineHasTime,
		Priority priority,
		TaskStatus status,
		LocalDateTime createdAt,
		int scheduledMinutes,
		int doneMinutes,
		boolean atRisk) {

	static TaskResponse of(Task task, int scheduledMinutes, int doneMinutes, boolean atRisk) {
		return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
				task.getEstimatedMinutes(), task.getDeadline(), task.isDeadlineHasTime(),
				task.getPriority(), task.getStatus(), task.getCreatedAt(), scheduledMinutes, doneMinutes, atRisk);
	}

}
