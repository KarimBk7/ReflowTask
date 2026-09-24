package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;

import dev.karimbk.reflowtask.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BlockState state = BlockState.PLANNED;

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

	/** Places the block where the user put it. A block the user positioned is pinned there. */
	public void moveTo(LocalDateTime startAt, LocalDateTime endAt) {
		this.startAt = startAt;
		this.endAt = endAt;
		this.pinned = true;
	}

	public BlockState getState() {
		return this.state;
	}

	public boolean isPlanned() {
		return this.state == BlockState.PLANNED;
	}

	public void markMissed() {
		this.state = BlockState.MISSED;
	}

	public void markDone() {
		this.state = BlockState.DONE;
	}

	/** Undoes {@link #markDone}. The next replan decides whether it is still ahead or now missed. */
	public void reopen() {
		this.state = BlockState.PLANNED;
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
