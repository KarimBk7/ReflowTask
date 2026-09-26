package dev.karimbk.reflowtask.calendar;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import dev.karimbk.reflowtask.schedule.TimeBlock;
import dev.karimbk.reflowtask.task.Priority;
import dev.karimbk.reflowtask.task.Task;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IcsWriterTests {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 8, 0);

	private static TimeBlock block(String title, String description, LocalDateTime start, int minutes) {
		Task task = new Task(1L, title, description, minutes, null, false, Priority.MEDIUM, NOW);
		return new TimeBlock(task, start, start.plusMinutes(minutes), false);
	}

	@Test
	void blocksAreWrittenInUtc() {
		String ics = IcsWriter.feed("Mine", List.of(block("Report", null, LocalDateTime.of(2026, 9, 28, 10, 0), 60)),
				NOW, BERLIN);

		// 10:00 in Berlin summer time is 08:00 UTC.
		assertThat(ics).contains("DTSTART:20260928T080000Z").contains("DTEND:20260928T090000Z").contains("SUMMARY:Report");
	}

	@Test
	void textIsEscapedAndLongLinesAreFoldedWithoutBreakingCharacters() {
		String title = "Plan; review, and ship \\ äöü ".repeat(6);
		String ics = IcsWriter.feed("Mine", List.of(block(title, "line one\nline two", NOW.plusHours(2), 30)), NOW, BERLIN);

		for (String line : ics.split("\r\n")) {
			assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
		}
		String unfolded = ics.replace("\r\n ", "");
		assertThat(unfolded).contains("Plan\\; review\\, and ship \\\\ äöü").contains("line one\\nline two");
	}

	@Test
	void missedPartsAreLeftOutAndDoneOnesMarked() {
		TimeBlock missed = block("Missed", null, NOW.minusHours(3), 60);
		missed.markMissed();
		TimeBlock done = block("Done", null, NOW.minusHours(2), 60);
		done.markDone();

		String ics = IcsWriter.feed("Mine", List.of(missed, done), NOW, BERLIN);

		assertThat(ics).doesNotContain("SUMMARY:Missed").contains("SUMMARY:✓ Done");
	}

	@Test
	void onlyPlannedWorkAheadGetsAReminder() {
		String ics = IcsWriter.feed("Mine",
				List.of(block("Ahead", null, NOW.plusHours(1), 60), block("Running", null, NOW.minusMinutes(10), 60)),
				NOW, BERLIN);

		assertThat(ics.split("BEGIN:VALARM", -1)).hasSize(2);
		assertThat(ics).contains("TRIGGER:-PT10M");
	}

	@Test
	void theFeedIsAValidCalendarThatReadsBackAsItsOwnEvents() throws Exception {
		String ics = IcsWriter.feed("Mine, with; symbols",
				List.of(block("One", "Notes", NOW.plusHours(1), 60), block("Two", null, NOW.plusHours(3), 90)), NOW, BERLIN);

		Calendar parsed = new CalendarBuilder().build(new StringReader(ics));
		assertThat(parsed.getComponents(Component.VEVENT)).hasSize(2);
		// Subscribing to a calendar that contains this feed must not make work block itself.
		assertThat(IcsReader.busyIn(ics, NOW, NOW.plusDays(1), BERLIN)).isEmpty();
	}

}
