package dev.karimbk.reflowtask.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "task")
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	private String description;

	/** Estimated effort. This, not the deadline, is what makes a task schedulable. */
	@Column(nullable = false)
	private int estimatedMinutes;

	/**
	 * When the task is due, or null for no deadline. A date-only deadline is stored as
	 * the last minute of that day, so comparisons need no special case.
	 */
	private LocalDateTime deadline;

	/** False when the user gave only a date, so the UI can render "Fri" not "Fri 23:59". */
	@Column(nullable = false)
	private boolean deadlineHasTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TaskStatus status;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected Task() {
		// for JPA
	}

	public Task(String title, String description, int estimatedMinutes, LocalDateTime deadline,
			boolean deadlineHasTime, Priority priority, LocalDateTime createdAt) {
		this.title = title;
		this.description = description;
		this.estimatedMinutes = estimatedMinutes;
		this.deadline = deadline;
		this.deadlineHasTime = deadlineHasTime;
		this.priority = priority;
		this.status = TaskStatus.OPEN;
		this.createdAt = createdAt;
	}

	/**
	 * Folds the API's separate date and optional time into one instant. A date without a
	 * time means "any time that day", so it resolves to the day's last minute.
	 */
	public static LocalDateTime toDeadline(LocalDate date, LocalTime time) {
		if (date == null) {
			return null;
		}
		return date.atTime(time != null ? time : LocalTime.of(23, 59));
	}

	public Long getId() {
		return this.id;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getEstimatedMinutes() {
		return this.estimatedMinutes;
	}

	public void setEstimatedMinutes(int estimatedMinutes) {
		this.estimatedMinutes = estimatedMinutes;
	}

	public LocalDateTime getDeadline() {
		return this.deadline;
	}

	public boolean isDeadlineHasTime() {
		return this.deadlineHasTime;
	}

	public void setDeadline(LocalDateTime deadline, boolean hasTime) {
		this.deadline = deadline;
		this.deadlineHasTime = deadline != null && hasTime;
	}

	public Priority getPriority() {
		return this.priority;
	}

	public void setPriority(Priority priority) {
		this.priority = priority;
	}

	public TaskStatus getStatus() {
		return this.status;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
