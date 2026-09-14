package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;

import dev.karimbk.reflowtask.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A concrete piece of a task placed on the calendar. A task may have several. */
@Entity
@Table(name = "time_block")
public class TimeBlock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false)
	private Task task;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	/** Immovable: replanning treats it as an obstacle instead of rescheduling it. */
	@Column(nullable = false)
	private boolean pinned;

	protected TimeBlock() {
		// for JPA
	}

	public TimeBlock(Task task, LocalDateTime startAt, LocalDateTime endAt, boolean pinned) {
		this.task = task;
		this.startAt = startAt;
		this.endAt = endAt;
		this.pinned = pinned;
	}

	public Long getId() {
		return this.id;
	}

	public Task getTask() {
		return this.task;
	}

	public LocalDateTime getStartAt() {
		return this.startAt;
	}

	public LocalDateTime getEndAt() {
		return this.endAt;
	}

	public boolean isPinned() {
		return this.pinned;
	}

	public void setPinned(boolean pinned) {
		this.pinned = pinned;
	}

	public TimeSlot toSlot() {
		return new TimeSlot(this.startAt, this.endAt);
	}

	/** True once the block's time is entirely behind us. */
	public boolean hasEndedBy(LocalDateTime now) {
		return !this.endAt.isAfter(now);
	}

	/** True when now falls inside the block: the user may be working on it right now. */
	public boolean isInFlightAt(LocalDateTime now) {
		return !this.startAt.isAfter(now) && this.endAt.isAfter(now);
	}

}
