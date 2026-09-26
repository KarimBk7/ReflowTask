package dev.karimbk.reflowtask.calendar;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import dev.karimbk.reflowtask.schedule.BlockState;
import dev.karimbk.reflowtask.schedule.TimeBlock;
import dev.karimbk.reflowtask.task.TaskStatus;

/**
 * Writes planned blocks as an iCalendar (.ics) feed for calendar apps to subscribe to.
 *
 * Times are written in UTC: every calendar app reads that, and it needs no time-zone definitions
 * in the file. The blocks themselves are wall-clock times where the working hours are, so they are
 * converted from that zone first. Hand-written rather than generated with a library: the format
 * for this is a few fixed lines, and the two rules that matter (escaping and line folding) are
 * short and tested.
 */
final class IcsWriter {

	/** Every event this app writes has a UID ending in this, which is how the reader skips them. */
	static final String UID_DOMAIN = "@reflowtask";

	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

	/** How long before a planned block the calendar app should remind, where it honours alarms. */
	private static final int REMINDER_MINUTES = 10;

	private IcsWriter() {
	}

	static String feed(String calendarName, List<TimeBlock> blocks, LocalDateTime now, ZoneId zone) {
		StringBuilder out = new StringBuilder();
		line(out, "BEGIN:VCALENDAR");
		line(out, "VERSION:2.0");
		line(out, "PRODID:-//ReflowTask//Planned blocks//EN");
		line(out, "CALSCALE:GREGORIAN");
		line(out, "METHOD:PUBLISH");
		line(out, "X-WR-CALNAME:" + escape(calendarName));
		// A hint only: many apps poll on their own schedule regardless.
		line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT15M");
		line(out, "X-PUBLISHED-TTL:PT15M");
		String stamp = utc(now, zone);
		for (TimeBlock block : blocks) {
			if (block.getState() == BlockState.MISSED) {
				continue;
			}
			boolean done = block.getState() == BlockState.DONE
					|| block.getTask().getStatus() == TaskStatus.DONE;
			line(out, "BEGIN:VEVENT");
			line(out, "UID:block-" + block.getId() + UID_DOMAIN);
			line(out, "DTSTAMP:" + stamp);
			line(out, "DTSTART:" + utc(block.getStartAt(), zone));
			line(out, "DTEND:" + utc(block.getEndAt(), zone));
			line(out, "SUMMARY:" + escape((done ? "✓ " : "") + block.getTask().getTitle()));
			String description = block.getTask().getDescription();
			line(out, "DESCRIPTION:" + escape(
					(description == null || description.isBlank() ? "" : description + "\n\n") + "Planned by ReflowTask."));
			line(out, "TRANSP:OPAQUE");
			if (!done && block.getStartAt().isAfter(now)) {
				line(out, "BEGIN:VALARM");
				line(out, "ACTION:DISPLAY");
				line(out, "DESCRIPTION:" + escape(block.getTask().getTitle()));
				line(out, "TRIGGER:-PT" + REMINDER_MINUTES + "M");
				line(out, "END:VALARM");
			}
			line(out, "END:VEVENT");
		}
		line(out, "END:VCALENDAR");
		return out.toString();
	}

	private static String utc(LocalDateTime wallClock, ZoneId zone) {
		return wallClock.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(UTC);
	}

	/** Text values escape backslash, semicolon and comma, and newlines become a literal \n. */
	static String escape(String text) {
		return text.replace("\\", "\\\\")
			.replace(";", "\\;")
			.replace(",", "\\,")
			.replace("\r\n", "\\n")
			.replace("\n", "\\n")
			.replace("\r", "\\n");
	}

	/**
	 * Lines end in CRLF and are folded at 75 octets, continuation lines starting with a space.
	 * Folding counts UTF-8 bytes and never splits a character, so umlauts and emoji survive.
	 */
	static void line(StringBuilder out, String content) {
		int used = 0;
		int limit = 75;
		for (int i = 0; i < content.length();) {
			int codePoint = content.codePointAt(i);
			int bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
			if (used + bytes > limit) {
				out.append("\r\n ");
				used = 0;
				limit = 74;
			}
			out.appendCodePoint(codePoint);
			used += bytes;
			i += Character.charCount(codePoint);
		}
		out.append("\r\n");
	}

}
