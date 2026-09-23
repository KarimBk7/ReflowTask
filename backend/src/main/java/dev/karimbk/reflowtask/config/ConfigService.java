package dev.karimbk.reflowtask.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import dev.karimbk.reflowtask.config.ConfigPayloads.Config;
import dev.karimbk.reflowtask.config.ConfigPayloads.Window;
import dev.karimbk.reflowtask.schedule.RescheduleTrigger;
import dev.karimbk.reflowtask.schedule.SchedulerService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigService {

	private static final Comparator<Window> BY_DAY_THEN_START = Comparator.comparing(Window::day)
		.thenComparing(Window::startTime);

	private final WorkingHoursRepository workingHours;

	private final BlockedPeriodRepository blockedPeriods;

	private final SchedulingSettingsRepository settings;

	private final SchedulerService scheduler;

	ConfigService(WorkingHoursRepository workingHours, BlockedPeriodRepository blockedPeriods,
			SchedulingSettingsRepository settings, SchedulerService scheduler) {
		this.workingHours = workingHours;
		this.blockedPeriods = blockedPeriods;
		this.settings = settings;
		this.scheduler = scheduler;
	}

	@Transactional(readOnly = true)
	public Config current(long userId) {
		SchedulingSettings current = this.settings.findOrDefault(userId);
		List<Window> working = this.workingHours.findByUserId(userId)
			.stream()
			.map((hours) -> new Window(hours.getDay(), hours.getStartTime(), hours.getEndTime(), null))
			.sorted(BY_DAY_THEN_START)
			.toList();
		List<Window> blocked = this.blockedPeriods.findByUserId(userId)
			.stream()
			.map((period) -> new Window(period.getDay(), period.getStartTime(), period.getEndTime(),
					period.getLabel()))
			.sorted(BY_DAY_THEN_START)
			.toList();
		if (!current.isOnboarded() && working.isEmpty()) {
			// A new member has no rows yet (only the first admin's were seeded by migration): offer
			// the same Monday-to-Friday default for the setup screen to start from.
			working = Stream
				.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
						DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
				.map((day) -> new Window(day, LocalTime.of(9, 0), LocalTime.of(18, 0), null))
				.toList();
		}
		return new Config(working, blocked, current.getHorizonDays(), current.getMinChunkMinutes(),
				current.getBufferMinutes(), current.isOnboarded());
	}

	/**
	 * Replaces the whole configuration in one transaction, then replans.
	 *
	 * The schedule is rebuilt inside the same transaction on purpose: saving new hours and
	 * leaving the old plan in place, even briefly, would show work outside the hours the user
	 * just set. If the replan fails, the configuration change rolls back with it.
	 */
	@Transactional
	public Config replace(long userId, Config config) {
		this.workingHours.deleteByUserId(userId);
		this.blockedPeriods.deleteByUserId(userId);
		// Hibernate executes inserts before deletes when it flushes, and working hours are keyed
		// by (user, weekday), so a replaced Monday could be inserted while the old row still
		// exists. Today that is avoided only incidentally: the id is assigned rather than
		// generated, so save() merges, and merge's existence lookup flushes these deletes first.
		// The explicit flush makes the ordering a guarantee instead of a side effect that a
		// switch to persist() would silently remove.
		this.workingHours.flush();

		this.workingHours.saveAll(config.workingHours()
			.stream()
			.map((window) -> new WorkingHours(userId, window.day(), window.startTime(), window.endTime()))
			.toList());
		this.blockedPeriods.saveAll(config.blockedPeriods()
			.stream()
			.map((window) -> new BlockedPeriod(userId, window.day(), window.startTime(), window.endTime(),
					blankToNull(window.label())))
			.toList());

		SchedulingSettings current = this.settings.findOrDefault(userId);
		boolean firstTimeSetup = !current.isOnboarded();
		current.setHorizonDays(config.horizonDays());
		current.setMinChunkMinutes(config.minChunkMinutes());
		current.setBufferMinutes(config.bufferMinutes());
		// Whatever the request says: saving the configuration is itself what onboarding means.
		current.markOnboarded();
		this.settings.save(current);
		this.settings.flush();

		if (firstTimeSetup) {
			this.scheduler.seedDemoMiss(userId);
		}
		this.scheduler.replan(userId, RescheduleTrigger.CONFIG_CHANGED);
		return current(userId);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
