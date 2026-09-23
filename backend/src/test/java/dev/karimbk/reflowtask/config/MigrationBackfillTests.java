package dev.karimbk.reflowtask.config;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5/V6 backfill every pre-existing row to the seeded admin (user id 1). The rows V2 seeds
 * (working hours, scheduling settings) exist before V4 creates {@code app_user} and before V5
 * runs, so a fresh migration run is itself an end-to-end exercise of that backfill - there is no
 * need to fabricate pre-V5 state by hand.
 */
@SpringBootTest
@Transactional
class MigrationBackfillTests {

	private static final long SEEDED_ADMIN_ID = 1L;

	@Autowired
	private WorkingHoursRepository workingHours;

	@Autowired
	private SchedulingSettingsRepository schedulingSettings;

	@Autowired
	private BlockedPeriodRepository blockedPeriods;

	@Test
	void everyV2SeededWorkingHourIsOwnedByTheSeededAdmin() {
		assertThat(this.workingHours.findByUserId(SEEDED_ADMIN_ID)).hasSize(5)
			.allSatisfy((hours) -> assertThat(hours.getUserId()).isEqualTo(SEEDED_ADMIN_ID));
	}

	@Test
	void theSeededSchedulingSettingsRowIsOwnedByTheSeededAdmin() {
		assertThat(this.schedulingSettings.findByUserId(SEEDED_ADMIN_ID)).isPresent()
			.get()
			.satisfies((settings) -> assertThat(settings.getUserId()).isEqualTo(SEEDED_ADMIN_ID));
	}

	@Test
	void blockedPeriodsTableHasNoOrphanedRowsAfterBackfill() {
		// V3 already removed the V2-seeded lunch breaks, so this only guards that whatever is
		// left (nothing, on a fresh install) is never unowned.
		assertThat(this.blockedPeriods.findAll()).allSatisfy((period) -> assertThat(period.getUserId()).isPositive());
	}

}
