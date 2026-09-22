package dev.karimbk.reflowtask.config;

import java.util.Comparator;
import java.util.List;

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
	public Config current() {
		SchedulingSettings current = currentSettings();
		List<Window> working = this.workingHours.findAll()
			.stream()
			.map((hours) -> new Window(hours.getDay(), hours.getStartTime(), hours.getEndTime(), null))
			.sorted(BY_DAY_THEN_START)
			.toList();
		List<Window> blocked = this.blockedPeriods.findAll()
			.stream()
			.map((period) -> new Window(period.getDay(), period.getStartTime(), period.getEndTime(),
					period.getLabel()))
			.sorted(BY_DAY_THEN_START)
			.toList();
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
	public Config replace(Config config) {
		this.workingHours.deleteAll();
		this.blockedPeriods.deleteAll();
		// Hibernate executes inserts before deletes when it flushes, and working hours are keyed
		// by weekday, so a replaced Monday could be inserted while the old row still exists.
		// Today that is avoided only incidentally: the id is assigned rather than generated, so
		// save() merges, and merge's existence lookup flushes these deletes first. (Removing this
		// line leaves the tests green, which is how that was established.) The explicit flush
		// makes the ordering a guarantee instead of a side effect that a switch to persist()
		// would silently remove.
		this.workingHours.flush();

		this.workingHours.saveAll(config.workingHours()
			.stream()
			.map((window) -> new WorkingHours(window.day(), window.startTime(), window.endTime()))
			.toList());
		this.blockedPeriods.saveAll(config.blockedPeriods()
			.stream()
			.map((window) -> new BlockedPeriod(window.day(), window.startTime(), window.endTime(),
					blankToNull(window.label())))
			.toList());

		SchedulingSettings current = currentSettings();
		boolean firstTimeSetup = !current.isOnboarded();
		current.setHorizonDays(config.horizonDays());
		current.setMinChunkMinutes(config.minChunkMinutes());
		current.setBufferMinutes(config.bufferMinutes());
		// Whatever the request says: saving the configuration is itself what onboarding means.
		current.markOnboarded();
		this.settings.save(current);
		this.settings.flush();

		if (firstTimeSetup) {
			this.scheduler.seedDemoMiss();
		}
		this.scheduler.replan(RescheduleTrigger.CONFIG_CHANGED);
		return current();
	}

	/** The settings row is seeded by migration; the fallback only keeps a damaged database usable. */
	private SchedulingSettings currentSettings() {
		return this.settings.findById(SchedulingSettings.SINGLETON_ID).orElseGet(() -> new SchedulingSettings(14, 30));
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
