package dev.karimbk.reflowtask.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload. Deadline arrives as a date plus an optional time rather than one
 * timestamp, because "due Friday" and "due Friday at 14:00" are different user intents and
 * the UI has to render them differently.
 *
 * Status is absent on purpose: it changes only through the status endpoint, so there is one
 * way to complete a task rather than two.
 */
public record TaskRequest(

		@NotBlank @Size(max = 200) String title,

		@Size(max = 2000) String description,

		// Upper bound is a sanity guard against absurd input, not a product rule about how
		// large a task may be; 30 days of effort is far past anything meaningful.
		@NotNull @Min(1) @Max(43_200) Integer estimatedMinutes,

		LocalDate deadlineDate,

		LocalTime deadlineTime,

		@NotNull Priority priority,

		// Create only, and optional: when set, the task is fixed at this time as a pinned block
		// instead of being placed by the scheduler. Ignored on update, where moving a block is
		// what changes its time.
		LocalDateTime fixedStart) {

	@AssertTrue(message = "deadlineTime requires deadlineDate")
	public boolean isDeadlineConsistent() {
		return this.deadlineDate != null || this.deadlineTime == null;
	}

}
