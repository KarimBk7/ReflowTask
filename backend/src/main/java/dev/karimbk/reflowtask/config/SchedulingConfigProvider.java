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

	private final WorkingHoursRepository workingHours;

	private final PersonalHoursRepository personalHours;

	private final BlockedPeriodRepository blockedPeriods;

	private final SchedulingSettingsRepository settings;

	SchedulingConfigProvider(WorkingHoursRepository workingHours, PersonalHoursRepository personalHours,
			BlockedPeriodRepository blockedPeriods, SchedulingSettingsRepository settings) {
		this.workingHours = workingHours;
		this.personalHours = personalHours;
		this.blockedPeriods = blockedPeriods;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public SchedulingConfig current(long userId) {
		SchedulingSettings current = this.settings.findOrDefault(userId);
		List<DailyWindow> working = this.workingHours.findByUserId(userId)
			.stream()
			.map((hours) -> new DailyWindow(hours.getDay(), hours.getStartTime(), hours.getEndTime()))
			.toList();
		if (DefaultHours.apply(current, !working.isEmpty())) {
			working = DefaultHours.WEEKDAYS;
		}
		List<DailyWindow> blocked = this.blockedPeriods.findByUserId(userId)
			.stream()
			.map((period) -> new DailyWindow(period.getDay(), period.getStartTime(), period.getEndTime()))
			.toList();
		List<DailyWindow> personal = this.personalHours.findByUserId(userId)
			.stream()
			.map((hours) -> new DailyWindow(hours.getDay(), hours.getStartTime(), hours.getEndTime()))
			.toList();
		return new SchedulingConfig(working, blocked, current.getHorizonDays(), current.getMinChunkMinutes(),
				current.getBufferMinutes(), current.getFreezeMinutes(), personal);
	}

}
