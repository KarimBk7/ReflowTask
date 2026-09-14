package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;
import java.util.List;

import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.TaskStatus;

/** Wire shapes for the schedule endpoints, grouped because each is a few lines. */
final class ScheduleResponses {

	private ScheduleResponses() {
	}

	/**
	 * @param atRisk the block ends after its task's deadline. Derived on read rather than
	 * stored, so it cannot disagree with the deadline it describes.
	 */
	record BlockView(Long id, Long taskId, String taskTitle, LocalDateTime startAt, LocalDateTime endAt,
			boolean pinned, boolean atRisk, Priority priority, TaskStatus status) {

		static BlockView of(TimeBlock block) {
			LocalDateTime deadline = block.getTask().getDeadline();
			boolean atRisk = deadline != null && block.getEndAt().isAfter(deadline);
			return new BlockView(block.getId(), block.getTask().getId(), block.getTask().getTitle(),
					block.getStartAt(), block.getEndAt(), block.isPinned(), atRisk, block.getTask().getPriority(),
					block.getTask().getStatus());
		}

	}

	record EventItemView(Long taskId, String taskTitle, RescheduleItemKind kind, LocalDateTime previousStartAt,
			LocalDateTime newStartAt) {

		static EventItemView of(RescheduleEventItem item) {
			return new EventItemView(item.getTaskId(), item.getTaskTitle(), item.getKind(), item.getPreviousStartAt(),
					item.getNewStartAt());
		}

	}

	record EventView(Long id, LocalDateTime occurredAt, RescheduleTrigger trigger, String summary,
			List<EventItemView> items) {

		static EventView of(RescheduleEvent event) {
			return new EventView(event.getId(), event.getOccurredAt(), event.getTriggerType(), event.getSummary(),
					event.getItems().stream().map(EventItemView::of).toList());
		}

	}

}
