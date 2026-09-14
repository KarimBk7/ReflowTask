package dev.karimbk.reflowtask.task;

import java.time.LocalDateTime;

public record TaskResponse(
		Long id,
		String title,
		String description,
		int estimatedMinutes,
		LocalDateTime deadline,
		boolean deadlineHasTime,
		Priority priority,
		TaskStatus status,
		LocalDateTime createdAt) {

	public static TaskResponse of(Task task) {
		return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
				task.getEstimatedMinutes(), task.getDeadline(), task.isDeadlineHasTime(),
				task.getPriority(), task.getStatus(), task.getCreatedAt());
	}

}
