package dev.karimbk.reflowtask.dataexport;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.karimbk.reflowtask.calendar.CalendarService;
import dev.karimbk.reflowtask.config.ConfigPayloads.Config;
import dev.karimbk.reflowtask.config.ConfigService;
import dev.karimbk.reflowtask.schedule.RescheduleEventRepository;
import dev.karimbk.reflowtask.schedule.ScheduleResponses.BlockView;
import dev.karimbk.reflowtask.schedule.ScheduleResponses.EventView;
import dev.karimbk.reflowtask.schedule.TimeBlockRepository;
import dev.karimbk.reflowtask.task.TaskResponse;
import dev.karimbk.reflowtask.task.TaskService;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything one person has in ReflowTask, to keep or to take elsewhere: tasks, their blocks, the
 * hours and settings, the calendars they read, and the replan history.
 */
@Service
class ExportService {

	/** Bumped if the shape ever changes in a way a reader of an older export would trip over. */
	static final String FORMAT = "reflowtask-export/1";

	private final TaskService tasks;

	private final TimeBlockRepository blocks;

	private final ConfigService config;

	private final CalendarService calendars;

	private final RescheduleEventRepository events;

	private final Clock clock;

	ExportService(TaskService tasks, TimeBlockRepository blocks, ConfigService config, CalendarService calendars,
			RescheduleEventRepository events, Clock clock) {
		this.tasks = tasks;
		this.blocks = blocks;
		this.config = config;
		this.calendars = calendars;
		this.events = events;
		this.clock = clock;
	}

	record Calendar(String name, String url, LocalDateTime lastFetchedAt, String lastError) {
	}

	record Export(String format, LocalDateTime exportedAt, String username, Config settings, List<TaskResponse> tasks,
			List<BlockView> blocks, List<Calendar> calendars, List<EventView> history) {
	}

	@Transactional(readOnly = true)
	Export export(long userId, String username) {
		return new Export(FORMAT, LocalDateTime.now(this.clock), username, this.config.current(userId),
				this.tasks.findAll(userId),
				this.blocks.findAllWithTaskByUserId(userId).stream().map(BlockView::of).toList(),
				this.calendars.sources(userId)
					.stream()
					.map((source) -> new Calendar(source.getName(), source.getUrl(), source.getLastFetchedAt(),
							source.getLastError()))
					.toList(),
				this.events.findByUserIdOrderByOccurredAtDesc(userId, Limit.unlimited())
					.stream()
					.map(EventView::of)
					.toList());
	}

	private static final List<String> COLUMNS = List.of("id", "title", "status", "priority", "estimatedMinutes",
			"doneMinutes", "scheduledMinutes", "deadline", "notBefore", "recurrence", "profile", "afterTaskId",
			"nextStartAt", "createdAt", "notes");

	/**
	 * The task list as CSV, one row per task. Starts with a byte order mark so Excel reads umlauts
	 * as UTF-8, and defuses cells a spreadsheet would run as a formula.
	 */
	@Transactional(readOnly = true)
	String tasksCsv(long userId) {
		Stream<String> rows = this.tasks.findAll(userId)
			.stream()
			.map((task) -> Stream
				.of(task.id(), task.title(), task.status(), task.priority(), task.estimatedMinutes(),
						task.doneMinutes(), task.scheduledMinutes(), task.deadline(), task.notBefore(),
						task.recurrence(), task.profile(), task.afterTaskId(), task.nextStartAt(), task.createdAt(),
						task.description())
				.map(ExportService::cell)
				.collect(Collectors.joining(",")));
		return "\uFEFF" + Stream.concat(Stream.of(String.join(",", COLUMNS)), rows)
			.collect(Collectors.joining("\r\n", "", "\r\n"));
	}

	static String cell(Object value) {
		if (value == null) {
			return "";
		}
		String text = value.toString();
		if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)) {
			text = "'" + text;
		}
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			text = "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

}
