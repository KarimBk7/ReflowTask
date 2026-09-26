package dev.karimbk.reflowtask.calendar;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;

/**
 * Reads the busy time out of an iCalendar (.ics) file for one window, recurring events expanded.
 *
 * What counts as busy follows what calendar apps themselves do: an event marked free (TRANSP:
 * TRANSPARENT) or cancelled does not block anything, and neither does an all-day event, which is
 * far more often a birthday or a reminder than a day really spent. A day that is really taken is
 * a day off under Hours. Events this app published itself are skipped, so subscribing to a
 * calendar that already contains ReflowTask's own feed cannot make work block itself.
 */
final class IcsReader {

	/** Guards against a rule like "every minute, forever" filling the database. */
	static final int MAX_INTERVALS = 5_000;

	record Busy(LocalDateTime start, LocalDateTime end, String title) {
	}

	private IcsReader() {
	}

	/**
	 * @throws IllegalArgumentException when the text is not a readable calendar
	 */
	static List<Busy> busyIn(String ics, LocalDateTime from, LocalDateTime until, ZoneId zone) {
		Calendar calendar;
		try {
			calendar = new CalendarBuilder().build(new StringReader(ics));
		}
		catch (IOException | ParserException | RuntimeException ex) {
			throw new IllegalArgumentException("That address did not return a calendar (.ics).", ex);
		}

		Period<ZonedDateTime> window = new Period<>(from.atZone(zone), until.atZone(zone));
		// A floating event (a clock time with no zone) is compared against a window without one.
		Period<LocalDateTime> floatingWindow = new Period<>(from, until);
		List<Busy> busy = new ArrayList<>();
		for (VEvent event : calendar.<VEvent>getComponents(Component.VEVENT)) {
			if (!blocksTime(event)) {
				continue;
			}
			String title = event.getProperty(Property.SUMMARY).map(Property::getValue).orElse(null);
			boolean floating = event.getStartDate().map((start) -> start.getDate() instanceof LocalDateTime).orElse(false);
			Set<Period<Temporal>> occurrences = event.calculateRecurrenceSet(floating ? floatingWindow : window);
			for (Period<Temporal> occurrence : occurrences) {
				LocalDateTime start = local(occurrence.getStart(), zone);
				LocalDateTime end = local(occurrence.getEnd(), zone);
				if (start == null || end == null || !end.isAfter(start) || !start.isBefore(until) || !end.isAfter(from)) {
					continue;
				}
				busy.add(new Busy(start, end, truncate(title)));
				if (busy.size() >= MAX_INTERVALS) {
					return sorted(busy);
				}
			}
		}
		return sorted(busy);
	}

	// ponytail: a single occurrence moved in the other calendar (RECURRENCE-ID) is read at its new
	// time, but its original slot also stays busy from the series. Handle overrides if it matters.
	private static boolean blocksTime(VEvent event) {
		boolean allDay = event.getStartDate().map((start) -> start.getDate() instanceof LocalDate).orElse(true);
		boolean free = event.getProperty(Property.TRANSP).map(Property::getValue).filter("TRANSPARENT"::equalsIgnoreCase).isPresent();
		boolean cancelled = event.getProperty(Property.STATUS).map(Property::getValue).filter("CANCELLED"::equalsIgnoreCase).isPresent();
		boolean ours = event.getProperty(Property.UID).map(Property::getValue).filter((uid) -> uid.endsWith(IcsWriter.UID_DOMAIN)).isPresent();
		return !allDay && !free && !cancelled && !ours;
	}

	/** Every time the file can express, as wall-clock time where the working hours are. */
	private static LocalDateTime local(Temporal value, ZoneId zone) {
		return switch (value) {
			case ZonedDateTime zoned -> zoned.withZoneSameInstant(zone).toLocalDateTime();
			case OffsetDateTime offset -> offset.atZoneSameInstant(zone).toLocalDateTime();
			case Instant instant -> instant.atZone(zone).toLocalDateTime();
			// A "floating" time has no zone by design: it means that clock time wherever you are.
			case LocalDateTime floating -> floating;
			default -> null;
		};
	}

	private static String truncate(String title) {
		return (title == null || title.length() <= 200) ? title : title.substring(0, 200);
	}

	private static List<Busy> sorted(List<Busy> busy) {
		busy.sort(Comparator.comparing(Busy::start));
		return busy;
	}

}
