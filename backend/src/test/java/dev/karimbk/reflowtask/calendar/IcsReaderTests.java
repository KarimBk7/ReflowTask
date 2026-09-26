package dev.karimbk.reflowtask.calendar;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import dev.karimbk.reflowtask.calendar.IcsReader.Busy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcsReaderTests {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

	/** Monday 2026-09-28 to Monday 2026-10-12: two weeks. */
	private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 28, 0, 0);

	private static final LocalDateTime UNTIL = LocalDateTime.of(2026, 10, 12, 0, 0);

	private static String calendar(String... events) {
		return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//EN\r\n" + String.join("", events) + "END:VCALENDAR\r\n";
	}

	private static String event(String uid, String... lines) {
		return "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:20260901T000000Z\r\n" + String.join("\r\n", lines)
				+ "\r\nEND:VEVENT\r\n";
	}

	private static List<Busy> read(String ics) {
		return IcsReader.busyIn(ics, FROM, UNTIL, BERLIN);
	}

	@Test
	void anEventInUtcIsReadAsWallClockTimeWhereTheWorkingHoursAre() {
		// 08:00 UTC is 10:00 in Berlin summer time.
		List<Busy> busy = read(calendar(event("a", "SUMMARY:Dentist", "DTSTART:20260929T080000Z", "DTEND:20260929T090000Z")));

		assertThat(busy).singleElement().satisfies((b) -> {
			assertThat(b.start()).isEqualTo(LocalDateTime.of(2026, 9, 29, 10, 0));
			assertThat(b.end()).isEqualTo(LocalDateTime.of(2026, 9, 29, 11, 0));
			assertThat(b.title()).isEqualTo("Dentist");
		});
	}

	@Test
	void anEventInAnotherTimeZoneIsConverted() {
		// 09:00 in New York is 15:00 in Berlin.
		List<Busy> busy = read(calendar(event("a", "SUMMARY:Call", "DTSTART;TZID=America/New_York:20260929T090000",
				"DTEND;TZID=America/New_York:20260929T093000")));

		assertThat(busy).singleElement()
			.satisfies((b) -> assertThat(b.start()).isEqualTo(LocalDateTime.of(2026, 9, 29, 15, 0)));
	}

	@Test
	void aFloatingTimeMeansThatClockTime() {
		List<Busy> busy = read(calendar(event("a", "DTSTART:20260930T140000", "DTEND:20260930T150000")));

		assertThat(busy).singleElement()
			.satisfies((b) -> assertThat(b.start()).isEqualTo(LocalDateTime.of(2026, 9, 30, 14, 0)));
	}

	@Test
	void aWeeklySeriesIsExpandedInsideTheWindowAndItsExceptionsLeftOut() {
		List<Busy> busy = read(calendar(event("weekly", "SUMMARY:Team meeting",
				"DTSTART;TZID=Europe/Berlin:20260901T100000", "DTEND;TZID=Europe/Berlin:20260901T110000",
				"RRULE:FREQ=WEEKLY;BYDAY=TU", "EXDATE;TZID=Europe/Berlin:20261006T100000")));

		// Tuesdays in the window: 29 Sep and 6 Oct; 6 Oct is excluded.
		assertThat(busy).extracting(Busy::start).containsExactly(LocalDateTime.of(2026, 9, 29, 10, 0));
	}

	@Test
	void aDailySeriesAcrossTheDaylightSavingChangeKeepsItsLocalTime() {
		// Berlin leaves summer time on 25 Oct 2026; a 09:00 meeting stays at 09:00.
		List<Busy> busy = IcsReader.busyIn(calendar(event("daily", "DTSTART;TZID=Europe/Berlin:20261023T090000",
				"DTEND;TZID=Europe/Berlin:20261023T093000", "RRULE:FREQ=DAILY;COUNT=5")),
				LocalDateTime.of(2026, 10, 22, 0, 0), LocalDateTime.of(2026, 10, 30, 0, 0), BERLIN);

		assertThat(busy).hasSize(5).allSatisfy((b) -> assertThat(b.start().getHour()).isEqualTo(9));
	}

	@Test
	void freeCancelledAllDayAndOwnEventsBlockNothing() {
		List<Busy> busy = read(calendar(
				event("free", "SUMMARY:Maybe", "TRANSP:TRANSPARENT", "DTSTART:20260929T080000Z", "DTEND:20260929T090000Z"),
				event("cancelled", "SUMMARY:Off", "STATUS:CANCELLED", "DTSTART:20260929T100000Z", "DTEND:20260929T110000Z"),
				event("birthday", "SUMMARY:Birthday", "DTSTART;VALUE=DATE:20260930", "DTEND;VALUE=DATE:20261001"),
				event("block-7" + IcsWriter.UID_DOMAIN, "SUMMARY:Own block", "DTSTART:20261001T080000Z",
						"DTEND:20261001T090000Z"),
				event("real", "SUMMARY:Real", "DTSTART:20261002T080000Z", "DTEND:20261002T090000Z")));

		assertThat(busy).extracting(Busy::title).containsExactly("Real");
	}

	@Test
	void eventsOutsideTheWindowAreLeftOut() {
		List<Busy> busy = read(calendar(event("past", "DTSTART:20260901T080000Z", "DTEND:20260901T090000Z"),
				event("far", "DTSTART:20261201T080000Z", "DTEND:20261201T090000Z")));

		assertThat(busy).isEmpty();
	}

	@Test
	void anEventWithADurationInsteadOfAnEndIsRead() {
		List<Busy> busy = read(calendar(event("dur", "DTSTART:20260929T080000Z", "DURATION:PT45M")));

		assertThat(busy).singleElement()
			.satisfies((b) -> assertThat(b.end()).isEqualTo(LocalDateTime.of(2026, 9, 29, 10, 45)));
	}

	@Test
	void somethingThatIsNotACalendarIsRejected() {
		assertThatThrownBy(() -> read("<html><body>Login required</body></html>"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void anEndlessFastSeriesIsCapped() {
		List<Busy> busy = read(calendar(event("flood", "DTSTART:20260928T000000Z", "DTEND:20260928T000100Z",
				"RRULE:FREQ=MINUTELY;INTERVAL=2")));

		assertThat(busy).hasSizeLessThanOrEqualTo(IcsReader.MAX_INTERVALS);
	}

}
