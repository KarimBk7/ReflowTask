package dev.karimbk.reflowtask.config;

import java.util.List;

import dev.karimbk.reflowtask.schedule.DailyWindow;
import dev.karimbk.reflowtask.schedule.SchedulingConfig;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Translates the persisted configuration into the plain value type the planner takes. This
 * is the only place JPA entities meet the scheduling core, which is what keeps the planner
 * free of persistence concerns.
 */
@Component
public class SchedulingConfigProvider {

	/** Used when the settings row is somehow absent, so scheduling degrades rather than fails. */
	private static final SchedulingSettings FALLBACK = new SchedulingSettings(14, 30);

	private final WorkingHoursRepository workingHours;

	private final BlockedPeriodRepository blockedPeriods;

	private final SchedulingSettingsRepository settings;

	SchedulingConfigProvider(WorkingHoursRepository workingHours, BlockedPeriodRepository blockedPeriods,
			SchedulingSettingsRepository settings) {
		this.workingHours = workingHours;
		this.blockedPeriods = blockedPeriods;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public SchedulingConfig current() {
		SchedulingSettings current = this.settings.findById(SchedulingSettings.SINGLETON_ID).orElse(FALLBACK);
		List<DailyWindow> working = this.workingHours.findAll()
			.stream()
			.map((hours) -> new DailyWindow(hours.getDay(), hours.getStartTime(), hours.getEndTime()))
			.toList();
		List<DailyWindow> blocked = this.blockedPeriods.findAll()
			.stream()
			.map((period) -> new DailyWindow(period.getDay(), period.getStartTime(), period.getEndTime()))
			.toList();
		return new SchedulingConfig(working, blocked, current.getHorizonDays(), current.getMinChunkMinutes());
	}

}
