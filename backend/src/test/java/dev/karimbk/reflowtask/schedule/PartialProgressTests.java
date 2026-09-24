package dev.karimbk.reflowtask.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import dev.karimbk.reflowtask.SettableClock;
import dev.karimbk.reflowtask.common.ConflictException;
import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.Task;
import dev.karimbk.reflowtask.task.TaskRepository;
import dev.karimbk.reflowtask.task.TaskStatus;
import dev.karimbk.reflowtask.user.AuthTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Work that was partly done. A part marked done counts against the estimate, so only the rest is
 * planned again; a missed part stays on the calendar as history and can still be marked done
 * afterwards, instead of the whole estimate being replanned on every miss.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartialProgressTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	private static final long USER_ID = 1L;

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		SettableClock testClock() {
			return new SettableClock(MONDAY.atTime(8, 0), ZoneId.of("Europe/Berlin"));
		}

	}

	@Autowired
	private SchedulerService scheduler;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private TimeBlockRepository blocks;

	@Autowired
	private SettableClock clock;

	@Autowired
	private MockMvc mvc;

	@BeforeEach
	void beforeTheWorkingDay() {
		this.clock.set(MONDAY.atTime(8, 0));
	}

	private Task givenTask(String title, int minutes) {
		return this.tasks
			.save(new Task(USER_ID, title, null, minutes, null, false, Priority.MEDIUM, MONDAY.atTime(7, 0)));
	}

	private List<TimeBlock> blocksOf(Task task) {
		return this.blocks.findByTaskId(task.getId())
			.stream()
			.sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
			.toList();
	}

	private List<TimeBlock> blocksOf(Task task, BlockState state) {
		return blocksOf(task).stream().filter((block) -> block.getState() == state).toList();
	}

	/**
	 * A 180-minute task split around a fixed 10:00-11:00 appointment: 09:00-10:00 and 11:00-13:00.
	 * Its 13:00 deadline is what splits it; in one piece after the appointment it would end late.
	 */
	private Task givenSplitTask() {
		Task appointment = givenTask("Appointment", 60);
		this.scheduler.fix(USER_ID, appointment, MONDAY.atTime(10, 0));
		Task report = this.tasks.save(new Task(USER_ID, "Report", null, 180, MONDAY.atTime(13, 0), true,
				Priority.MEDIUM, MONDAY.atTime(7, 0)));
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		assertThat(blocksOf(report)).extracting(TimeBlock::getStartAt)
			.containsExactly(MONDAY.atTime(9, 0), MONDAY.atTime(11, 0));
		return report;
	}

	// --- a missed part stays visible -------------------------------------------------

	@Test
	void aMissedBlockStaysOnTheCalendarAsMissedAndTheWorkIsPlannedAgain() {
		Task task = givenTask("Write docs", 120);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);

		this.clock.set(MONDAY.atTime(14, 0));
		this.scheduler.replan(USER_ID, RescheduleTrigger.SCHEDULED_JOB);

		assertThat(blocksOf(task, BlockState.MISSED)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(9, 0)));
		assertThat(blocksOf(task, BlockState.PLANNED)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(14, 0)));
	}

	@Test
	void aMissIsReportedOnceNotOnEveryLaterReplan() {
		givenTask("Write docs", 120);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		this.clock.set(MONDAY.atTime(14, 0));
		assertThat(this.scheduler.replan(USER_ID, RescheduleTrigger.SCHEDULED_JOB)).isPresent();

		this.clock.set(MONDAY.atTime(15, 0));

		assertThat(this.scheduler.replan(USER_ID, RescheduleTrigger.SCHEDULED_JOB)).isEmpty();
	}

	// --- marking a part done --------------------------------------------------------

	@Test
	void aPartMarkedDoneWhileRunningIsNotMissedAndOnlyTheRestStaysPlanned() {
		Task report = givenSplitTask();
		TimeBlock first = blocksOf(report).get(0);

		this.clock.set(MONDAY.atTime(9, 45));
		this.scheduler.completeBlock(USER_ID, first.getId());
		this.clock.set(MONDAY.atTime(10, 30));
		Optional<RescheduleEvent> event = this.scheduler.replan(USER_ID, RescheduleTrigger.SCHEDULED_JOB);

		assertThat(event).isEmpty();
		assertThat(blocksOf(report, BlockState.DONE)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(9, 0)));
		assertThat(blocksOf(report, BlockState.PLANNED)).singleElement().satisfies((block) -> {
			assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(11, 0));
			assertThat(block.toSlot().minutes()).isEqualTo(120);
		});
		assertThat(this.tasks.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.OPEN);
	}

	@Test
	void aMissedPartMarkedDoneAfterwardsShrinksWhatIsReplanned() {
		Task report = givenSplitTask();
		// 09:00-10:00 ran out unfinished; at 10:30 it is a miss, and 180 minutes are planned again.
		this.clock.set(MONDAY.atTime(10, 30));
		this.scheduler.replan(USER_ID, RescheduleTrigger.SCHEDULED_JOB);
		TimeBlock missed = blocksOf(report, BlockState.MISSED).get(0);
		assertThat(plannedMinutes(report)).isEqualTo(180);

		// It was done after all.
		this.scheduler.completeBlock(USER_ID, missed.getId());

		assertThat(this.blocks.findById(missed.getId()).orElseThrow().getState()).isEqualTo(BlockState.DONE);
		assertThat(plannedMinutes(report)).isEqualTo(120);
	}

	@Test
	void theLastPartDoneFinishesTheTask() {
		Task task = givenTask("Write docs", 120);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		this.clock.set(MONDAY.atTime(14, 0));
		this.scheduler.replan(USER_ID, RescheduleTrigger.SCHEDULED_JOB);
		TimeBlock missed = blocksOf(task, BlockState.MISSED).get(0);

		this.scheduler.completeBlock(USER_ID, missed.getId());

		assertThat(this.tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.DONE);
		// The replacement that had just started at 14:00 keeps its time, as any running block of a
		// finished task does; nothing further ahead is planned.
		assertThat(blocksOf(task, BlockState.PLANNED))
			.allSatisfy((block) -> assertThat(block.getStartAt()).isBeforeOrEqualTo(MONDAY.atTime(14, 0)));
	}

	@Test
	void aPartThatHasNotStartedCannotBeMarkedDone() {
		Task task = givenTask("Later", 60);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		TimeBlock ahead = blocksOf(task).get(0);

		assertThatThrownBy(() -> this.scheduler.completeBlock(USER_ID, ahead.getId()))
			.isInstanceOf(ConflictException.class);
		assertThat(this.blocks.findById(ahead.getId()).orElseThrow().getState()).isEqualTo(BlockState.PLANNED);
	}

	@Test
	void finishingARunningPartIsNotReportedAsTheTaskMoving() {
		Task report = givenSplitTask();
		this.clock.set(MONDAY.atTime(9, 30));

		Optional<RescheduleEvent> event = this.scheduler.completeBlock(USER_ID, blocksOf(report).get(0).getId());

		assertThat(event).isEmpty();
	}

	@Test
	void undoingADonePartOpensTheTaskAgain() {
		Task task = givenTask("Write docs", 60);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		TimeBlock block = blocksOf(task).get(0);
		this.clock.set(MONDAY.atTime(9, 30));
		this.scheduler.completeBlock(USER_ID, block.getId());
		assertThat(this.tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.DONE);

		this.scheduler.reopenBlock(USER_ID, block.getId());

		assertThat(this.tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.OPEN);
		assertThat(this.blocks.findById(block.getId()).orElseThrow().getState()).isEqualTo(BlockState.PLANNED);
		assertThat(plannedMinutes(task)).isEqualTo(60);
	}

	@Test
	void undoingAPartWhoseTimeIsOverMakesItAMiss() {
		Task task = givenTask("Write docs", 60);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		TimeBlock block = blocksOf(task).get(0);
		this.clock.set(MONDAY.atTime(9, 30));
		this.scheduler.completeBlock(USER_ID, block.getId());
		this.clock.set(MONDAY.atTime(11, 0));

		this.scheduler.reopenBlock(USER_ID, block.getId());

		assertThat(this.blocks.findById(block.getId()).orElseThrow().getState()).isEqualTo(BlockState.MISSED);
		assertThat(blocksOf(task, BlockState.PLANNED)).singleElement()
			.satisfies((planned) -> assertThat(planned.getStartAt()).isEqualTo(MONDAY.atTime(11, 0)));
	}

	private long plannedMinutes(Task task) {
		return blocksOf(task, BlockState.PLANNED).stream().mapToLong((block) -> block.toSlot().minutes()).sum();
	}

	// --- over the API ---------------------------------------------------------------

	private Cookie login() throws Exception {
		return new AuthTestSupport(this.mvc).login();
	}

	@Test
	void theApiMarksAPartDoneAndReportsProgressOnTheTask() throws Exception {
		Task report = givenSplitTask();
		TimeBlock first = blocksOf(report).get(0);
		Cookie cookie = login();
		this.clock.set(MONDAY.atTime(9, 30));

		this.mvc.perform(post("/api/v1/schedule/blocks/" + first.getId() + "/done").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("DONE"));

		this.mvc.perform(get("/api/v1/tasks/" + report.getId()).cookie(cookie))
			.andExpect(jsonPath("$.doneMinutes").value(60))
			.andExpect(jsonPath("$.scheduledMinutes").value(180))
			.andExpect(jsonPath("$.status").value("OPEN"));

		this.mvc.perform(post("/api/v1/schedule/blocks/" + first.getId() + "/undone").cookie(cookie))
			.andExpect(status().isOk());
		this.mvc.perform(get("/api/v1/tasks/" + report.getId()).cookie(cookie))
			.andExpect(jsonPath("$.doneMinutes").value(0));
	}

	@Test
	void missedHistoryDoesNotCountAsScheduledOrAtRisk() throws Exception {
		Task task = this.tasks.save(new Task(USER_ID, "Due at noon", null, 60, MONDAY.atTime(12, 0), true,
				Priority.HIGH, MONDAY.atTime(7, 0)));
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		this.clock.set(MONDAY.atTime(10, 30));
		this.scheduler.replan(USER_ID, RescheduleTrigger.SCHEDULED_JOB);

		this.mvc.perform(get("/api/v1/tasks/" + task.getId()).cookie(login()))
			.andExpect(jsonPath("$.scheduledMinutes").value(60))
			.andExpect(jsonPath("$.doneMinutes").value(0))
			.andExpect(jsonPath("$.atRisk").value(false));
	}

	@Test
	void aMissedOrDonePartCannotBeDragged() throws Exception {
		Task task = givenTask("Write docs", 60);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		TimeBlock block = blocksOf(task).get(0);
		this.clock.set(MONDAY.atTime(9, 30));
		this.scheduler.completeBlock(USER_ID, block.getId());

		this.mvc.perform(patch("/api/v1/schedule/blocks/" + block.getId()).cookie(login())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(MONDAY.atTime(15, 0), MONDAY.atTime(16, 0))))
			.andExpect(status().isConflict());
	}

	@Test
	void markingAPartAheadOfTimeIsAConflictOverTheApi() throws Exception {
		Task task = givenTask("Later", 60);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);

		this.mvc.perform(post("/api/v1/schedule/blocks/" + blocksOf(task).get(0).getId() + "/done").cookie(login()))
			.andExpect(status().isConflict());
	}

	@Test
	void anotherUsersBlockCannotBeMarkedDone() throws Exception {
		Task task = givenTask("Admin only", 60);
		this.scheduler.replan(USER_ID, RescheduleTrigger.TASK_CHANGED);
		TimeBlock block = blocksOf(task).get(0);
		AuthTestSupport auth = new AuthTestSupport(this.mvc);
		Cookie other = auth.memberSession(auth.login(), "someone-else");

		this.mvc.perform(post("/api/v1/schedule/blocks/" + block.getId() + "/done").cookie(other))
			.andExpect(status().isNotFound());
		assertThat(this.blocks.findById(block.getId()).orElseThrow().getState()).isEqualTo(BlockState.PLANNED);
	}

}
