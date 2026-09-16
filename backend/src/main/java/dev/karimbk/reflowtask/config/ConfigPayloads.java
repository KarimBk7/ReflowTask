package dev.karimbk.reflowtask.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Wire shapes for the configuration endpoints. Reads and writes share one shape, so a client
 * can fetch the configuration, change a field and send it straight back.
 */
final class ConfigPayloads {

	private ConfigPayloads() {
	}

	/** A recurring weekly window. The label is only meaningful on a blocked period. */
	record Window(@NotNull DayOfWeek day, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
			@Size(max = 100) String label) {

		@AssertTrue(message = "must end after it starts")
		public boolean isOrdered() {
			// Missing fields are reported by @NotNull; do not report them twice here.
			return this.startTime == null || this.endTime == null || this.endTime.isAfter(this.startTime);
		}

	}

	/**
	 * The whole configuration, replaced as one unit.
	 *
	 * The upper bounds are guards on the planner rather than product rules: the horizon sets
	 * how many days every replan walks, so an unbounded one would let a single request make
	 * scheduling arbitrarily slow.
	 */
	record Config(@NotNull @Valid List<Window> workingHours, @NotNull @Valid List<Window> blockedPeriods,
			@NotNull @Min(1) @Max(366) Integer horizonDays, @NotNull @Min(1) @Max(1440) Integer minChunkMinutes) {

		/**
		 * Working hours are keyed by weekday, so a second window for the same day would be a
		 * primary-key violation - reported here as a validation error instead of a 500.
		 */
		@AssertTrue(message = "each weekday may have only one working window")
		public boolean isOneWindowPerDay() {
			if (this.workingHours == null) {
				return true;
			}
			Set<DayOfWeek> seen = new HashSet<>();
			return this.workingHours.stream().allMatch((window) -> window == null || seen.add(window.day()));
		}

	}

}
