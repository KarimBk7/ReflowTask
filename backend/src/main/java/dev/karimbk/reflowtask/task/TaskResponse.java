package dev.karimbk.reflowtask.task;

import java.time.LocalDateTime;

/**
 * @param scheduledMinutes how much of the estimate currently has a place on the schedule,
 * across all of the task's blocks. Less than {@code estimatedMinutes} means the horizon ran out
 * of room. Derived on every read rather than stored, so it cannot disagree with the schedule.
 * @param doneMinutes how much of it was marked done, part by part
 * @param atRisk some of the task's work is placed after its deadline
 * @param recurrence how often it comes back, or null for a one-off
 * @param notBefore work is not planned before this, or null
 * @param profile whether it is planned in working hours or personal time
 * @param nextStartAt when the next planned part starts (or the one running now), or null when
 * nothing is planned ahead
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
		boolean atRisk,
		Recurrence recurrence,
		LocalDateTime notBefore,
		TimeProfile profile,
		LocalDateTime nextStartAt) {

	static TaskResponse of(Task task, int scheduledMinutes, int doneMinutes, boolean atRisk,
			LocalDateTime nextStartAt) {
		return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
				task.getEstimatedMinutes(), task.getDeadline(), task.isDeadlineHasTime(),
				task.getPriority(), task.getStatus(), task.getCreatedAt(), scheduledMinutes, doneMinutes, atRisk,
				task.getRecurrence(), task.getNotBefore(), task.getProfile(), nextStartAt);
	}

}
