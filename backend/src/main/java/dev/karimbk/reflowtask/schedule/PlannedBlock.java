package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;

/** One piece of work the planner decided to place. Not yet persisted. */
public record PlannedBlock(long taskId, LocalDateTime start, LocalDateTime end) {

	public long minutes() {
		return new TimeSlot(this.start, this.end).minutes();
	}

}
