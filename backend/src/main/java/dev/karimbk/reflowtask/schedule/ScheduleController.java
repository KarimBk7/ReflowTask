package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;
import java.util.List;

import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.schedule.ScheduleResponses.BlockView;
import dev.karimbk.reflowtask.schedule.ScheduleResponses.EventView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Limit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class ScheduleController {

	private static final int EVENT_HISTORY_LIMIT = 50;

	private final TimeBlockRepository blocks;

	private final RescheduleEventRepository events;

	private final SchedulerService scheduler;

	ScheduleController(TimeBlockRepository blocks, RescheduleEventRepository events, SchedulerService scheduler) {
		this.blocks = blocks;
		this.events = events;
		this.scheduler = scheduler;
	}

	/** The week view's data: every block overlapping the requested range. */
	@GetMapping("/schedule")
	@Transactional(readOnly = true)
	List<BlockView> schedule(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
		return this.blocks.findOverlapping(from, to).stream().map(BlockView::of).toList();
	}

	@PostMapping("/schedule/replan")
	List<EventView> replan() {
		return this.scheduler.replan(RescheduleTrigger.MANUAL).map(EventView::of).map(List::of).orElseGet(List::of);
	}

	/** Where a dragged block was dropped. */
	record MoveRequest(@NotNull LocalDateTime startAt, @NotNull LocalDateTime endAt) {

		@AssertTrue(message = "must end after it starts")
		public boolean isOrdered() {
			return this.startAt == null || this.endAt == null || this.endAt.isAfter(this.startAt);
		}

	}

	/** Moves or resizes a block, pins it there, and replans everything else around it. */
	@PatchMapping("/schedule/blocks/{id}")
	@Transactional
	BlockView move(@PathVariable long id, @Valid @RequestBody MoveRequest request) {
		return BlockView.of(this.scheduler.move(id, request.startAt(), request.endAt()));
	}

	@PostMapping("/schedule/blocks/{id}/pin")
	@Transactional
	BlockView pin(@PathVariable long id) {
		return setPinned(id, true);
	}

	@PostMapping("/schedule/blocks/{id}/unpin")
	@Transactional
	BlockView unpin(@PathVariable long id) {
		return setPinned(id, false);
	}

	/** Visible history, so a schedule that rearranged itself can be explained. */
	@GetMapping("/reschedule-events")
	@Transactional(readOnly = true)
	List<EventView> rescheduleEvents() {
		return this.events.findAllByOrderByOccurredAtDesc(Limit.of(EVENT_HISTORY_LIMIT))
			.stream()
			.map(EventView::of)
			.toList();
	}

	private BlockView setPinned(long id, boolean pinned) {
		TimeBlock block = this.blocks.findById(id).orElseThrow(() -> new NotFoundException("Time block", id));
		block.setPinned(pinned);
		return BlockView.of(block);
	}

}
