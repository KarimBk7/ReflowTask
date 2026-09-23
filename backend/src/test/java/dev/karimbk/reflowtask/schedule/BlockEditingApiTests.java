package dev.karimbk.reflowtask.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.karimbk.reflowtask.SettableClock;
import dev.karimbk.reflowtask.task.TaskRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Putting work at a time of the user's choosing: creating a task fixed at a slot, and dragging a
 * block to move or resize it. Both override the scheduler, so both pin, and both are refused
 * when they would collide with something else the user already fixed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockEditingApiTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		SettableClock testClock() {
			return new SettableClock(MONDAY.atTime(8, 0), ZoneId.of("Europe/Berlin"));
		}

	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SettableClock clock;

	@Autowired
	private TimeBlockRepository blocks;

	@Autowired
	private TaskRepository tasks;

	private Cookie cookie;

	@BeforeEach
	void beforeTheWorkingDay() throws Exception {
		this.clock.set(MONDAY.atTime(8, 0));
		this.cookie = new AuthTestSupport(this.mvc).login();
	}

	private long createTask(String title, int minutes, String fixedStart) throws Exception {
		String body = """
				{"title":"%s","estimatedMinutes":%d,"priority":"MEDIUM","fixedStart":%s}"""
			.formatted(title, minutes, fixedStart == null ? "null" : "\"" + fixedStart + "\"");
		String response = this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie).contentType(MediaType.APPLICATION_JSON).content(body))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private List<TimeBlock> blocksOf(long taskId) {
		return this.blocks.findByTaskId(taskId)
			.stream()
			.sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
			.toList();
	}

	private static String iso(int hour, int minute) {
		return MONDAY.atTime(hour, minute).toString();
	}

	private static String move(int startHour, int startMinute, int endHour, int endMinute) {
		return """
				{"startAt":"%s","endAt":"%s"}""".formatted(iso(startHour, startMinute), iso(endHour, endMinute));
	}

	// --- creating at a fixed time ---------------------------------------------------

	@Test
	void aTaskCreatedAtAFixedTimeGetsAPinnedBlockExactlyThere() throws Exception {
		long id = createTask("Dentist", 60, iso(14, 0));

		assertThat(blocksOf(id)).singleElement().satisfies((block) -> {
			assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(14, 0));
			assertThat(block.getEndAt()).isEqualTo(MONDAY.atTime(15, 0));
			assertThat(block.isPinned()).isTrue();
		});
	}

	@Test
	void flexibleWorkIsReplannedAroundAFixedTask() throws Exception {
		long flexible = createTask("Flexible", 60, null);
		assertThat(blocksOf(flexible).get(0).getStartAt()).isEqualTo(MONDAY.atTime(9, 0));

		createTask("Standup", 60, iso(9, 0));

		assertThat(blocksOf(flexible)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(10, 0)));
	}

	@Test
	void aFixedTaskMayBeOutsideWorkingHours() throws Exception {
		// A real appointment can fall after work; fixing it is the user's call, not the planner's.
		long id = createTask("Evening class", 60, iso(19, 0));

		assertThat(blocksOf(id)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(19, 0)));
	}

	@Test
	void aFixedTimeOverlappingAnotherFixedBlockIsRefused() throws Exception {
		createTask("Dentist", 60, iso(14, 0));
		long taskCount = this.tasks.count();

		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Clash","estimatedMinutes":60,"priority":"MEDIUM","fixedStart":"%s"}"""
						.formatted(iso(14, 30))))
			.andExpect(status().isConflict());

		// Refused as a whole: the clashing task was not left behind without its block.
		assertThat(this.tasks.count()).isEqualTo(taskCount);
	}

	@Test
	void aFixedTimeInThePastIsRefused() throws Exception {
		this.clock.set(MONDAY.atTime(12, 0));

		this.mvc
			.perform(post("/api/v1/tasks").cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Too late","estimatedMinutes":60,"priority":"MEDIUM","fixedStart":"%s"}"""
						.formatted(iso(9, 0))))
			.andExpect(status().isConflict());
	}

	// --- moving and resizing --------------------------------------------------------

	@Test
	void movingABlockPinsItThereAndReplansOtherWorkAroundIt() throws Exception {
		long moved = createTask("Moved", 60, null);
		long other = createTask("Other", 60, null);
		long blockId = blocksOf(moved).get(0).getId();

		this.mvc
			.perform(patch("/api/v1/schedule/blocks/" + blockId).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(move(11, 0, 12, 0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.startAt").value("%s:00".formatted(iso(11, 0))))
			.andExpect(jsonPath("$.pinned").value(true));

		assertThat(blocksOf(moved)).singleElement()
			.satisfies((block) -> assertThat(block.getStartAt()).isEqualTo(MONDAY.atTime(11, 0)));
		// The morning the moved block left is taken by the other task.
		assertThat(blocksOf(other).get(0).getStartAt()).isEqualTo(MONDAY.atTime(9, 0));
	}

	@Test
	void resizingABlockChangesTheTaskEstimateByTheSameAmount() throws Exception {
		long id = createTask("Report", 60, null);
		long blockId = blocksOf(id).get(0).getId();

		// 09:00-10:00 stretched to 09:00-10:30.
		this.mvc
			.perform(patch("/api/v1/schedule/blocks/" + blockId).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(move(9, 0, 10, 30)))
			.andExpect(status().isOk());

		assertThat(this.tasks.findById(id)).get()
			.satisfies((task) -> assertThat(task.getEstimatedMinutes()).isEqualTo(90));
	}

	@Test
	void movingABlockOntoAnotherFixedBlockIsRefused() throws Exception {
		createTask("Dentist", 60, iso(14, 0));
		long id = createTask("Report", 60, null);
		long blockId = blocksOf(id).get(0).getId();

		this.mvc
			.perform(patch("/api/v1/schedule/blocks/" + blockId).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(move(14, 30, 15, 30)))
			.andExpect(status().isConflict());
	}

	@Test
	void movingABlockOfACompletedTaskIsRefused() throws Exception {
		long id = createTask("Done already", 60, null);
		long blockId = blocksOf(id).get(0).getId();
		this.tasks.findById(id).orElseThrow().setStatus(dev.karimbk.reflowtask.task.TaskStatus.DONE);

		this.mvc
			.perform(patch("/api/v1/schedule/blocks/" + blockId).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(move(11, 0, 12, 0)))
			.andExpect(status().isConflict());
	}

	@Test
	void movingABlockIntoThePastIsRefused() throws Exception {
		long id = createTask("Report", 60, null);
		long blockId = blocksOf(id).get(0).getId();
		this.clock.set(MONDAY.atTime(9, 30));

		this.mvc
			.perform(patch("/api/v1/schedule/blocks/" + blockId).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(move(7, 0, 8, 0)))
			.andExpect(status().isConflict());
	}

	@Test
	void aMoveThatEndsBeforeItStartsIsRejected() throws Exception {
		long id = createTask("Report", 60, null);
		long blockId = blocksOf(id).get(0).getId();

		this.mvc
			.perform(patch("/api/v1/schedule/blocks/" + blockId).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(move(12, 0, 11, 0)))
			.andExpect(status().isBadRequest());
	}

	/**
	 * A drag that needs nothing else to move is still a change worth recording. Without this,
	 * the board's "what changed" record silently misses every drag that had no side effects -
	 * which is most of them - and an old ghost from an earlier, unrelated move keeps showing
	 * long after the block has moved again.
	 */
	@Test
	void movingABlockWithNoSideEffectsIsStillRecordedAsAMove() throws Exception {
		long id = createTask("Report", 60, null);
		long blockId = blocksOf(id).get(0).getId();
		// A tick apart, so the two events sort unambiguously by time - a real clock never ties.
		this.clock.set(MONDAY.atTime(8, 1));

		this.mvc
			.perform(patch("/api/v1/schedule/blocks/" + blockId).cookie(this.cookie)
					.contentType(MediaType.APPLICATION_JSON)
					.content(move(11, 0, 12, 0)))
			.andExpect(status().isOk());

		this.mvc.perform(get("/api/v1/reschedule-events").cookie(this.cookie))
			.andExpect(jsonPath("$[0].items[0].taskTitle").value("Report"))
			.andExpect(jsonPath("$[0].items[0].kind").value("MOVED"))
			.andExpect(jsonPath("$[0].items[0].previousStartAt").value("%s:00".formatted(iso(9, 0))))
			.andExpect(jsonPath("$[0].items[0].newStartAt").value("%s:00".formatted(iso(11, 0))));
	}

}
