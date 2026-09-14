package dev.karimbk.reflowtask.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the scheduling configuration so the week view can draw the window the scheduler
 * actually planned against. Without this the board would have to hardcode working hours and
 * would quietly disagree with the plan it is displaying.
 */
@RestController
@RequestMapping("/api/v1/config")
class ConfigController {

	private final WorkingHoursRepository workingHours;

	private final BlockedPeriodRepository blockedPeriods;

	private final SchedulingSettingsRepository settings;

	ConfigController(WorkingHoursRepository workingHours, BlockedPeriodRepository blockedPeriods,
			SchedulingSettingsRepository settings) {
		this.workingHours = workingHours;
		this.blockedPeriods = blockedPeriods;
		this.settings = settings;
	}

	record WindowView(DayOfWeek day, LocalTime startTime, LocalTime endTime, String label) {
	}

	record ConfigView(List<WindowView> workingHours, List<WindowView> blockedPeriods, int horizonDays,
			int minChunkMinutes) {
	}

	@GetMapping
	@Transactional(readOnly = true)
	ConfigView current() {
		SchedulingSettings current = this.settings.findById(SchedulingSettings.SINGLETON_ID)
			.orElse(new SchedulingSettings(14, 30));
		return new ConfigView(
				this.workingHours.findAll()
					.stream()
					.map((hours) -> new WindowView(hours.getDay(), hours.getStartTime(), hours.getEndTime(), null))
					.sorted((a, b) -> a.day().compareTo(b.day()))
					.toList(),
				this.blockedPeriods.findAll()
					.stream()
					.map((period) -> new WindowView(period.getDay(), period.getStartTime(), period.getEndTime(),
							period.getLabel()))
					.toList(),
				current.getHorizonDays(), current.getMinChunkMinutes());
	}

}
