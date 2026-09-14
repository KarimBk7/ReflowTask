package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What happened to one task in one replan.
 *
 * The task title is copied rather than joined, and task_id is nulled instead of cascaded on
 * delete: history that disappears along with its task is not history. One row per task, not
 * per block, because "moved from Monday 09:00 to Tuesday 14:00" is what a person wants to
 * read, not a list of every fragment.
 */
@Entity
@Table(name = "reschedule_event_item")
public class RescheduleEventItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "task_id")
	private Long taskId;

	@Column(nullable = false)
	private String taskTitle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RescheduleItemKind kind;

	private LocalDateTime previousStartAt;

	private LocalDateTime newStartAt;

	protected RescheduleEventItem() {
		// for JPA
	}

	public RescheduleEventItem(Long taskId, String taskTitle, RescheduleItemKind kind, LocalDateTime previousStartAt,
			LocalDateTime newStartAt) {
		this.taskId = taskId;
		this.taskTitle = taskTitle;
		this.kind = kind;
		this.previousStartAt = previousStartAt;
		this.newStartAt = newStartAt;
	}

	public Long getId() {
		return this.id;
	}

	public Long getTaskId() {
		return this.taskId;
	}

	public String getTaskTitle() {
		return this.taskTitle;
	}

	public RescheduleItemKind getKind() {
		return this.kind;
	}

	public LocalDateTime getPreviousStartAt() {
		return this.previousStartAt;
	}

	public LocalDateTime getNewStartAt() {
		return this.newStartAt;
	}

}
