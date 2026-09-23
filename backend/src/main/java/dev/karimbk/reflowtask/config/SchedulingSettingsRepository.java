package dev.karimbk.reflowtask.config;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulingSettingsRepository extends JpaRepository<SchedulingSettings, Long> {

	Optional<SchedulingSettings> findByUserId(long userId);

	/** The settings row is seeded on first save; the fallback only keeps a fresh user usable. */
	default SchedulingSettings findOrDefault(long userId) {
		return findByUserId(userId).orElseGet(() -> new SchedulingSettings(userId, 14, 30));
	}

}
