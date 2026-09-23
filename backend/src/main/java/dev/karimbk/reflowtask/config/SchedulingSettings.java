package dev.karimbk.reflowtask.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row per user; a unique constraint on {@code userId} is what keeps it one row. */
@Entity
@Table(name = "scheduling_settings")
public class SchedulingSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long userId;

	@Column(nullable = false)
	private int horizonDays;

	@Column(nullable = false)
	private int minChunkMinutes;

	/** Minutes kept free between scheduled tasks and around fixed blocks. */
	@Column(nullable = false)
	private int bufferMinutes;

	/** False until the owner first saves their hours; drives the first-run setup. */
	@Column(nullable = false)
	private boolean onboarded;

	protected SchedulingSettings() {
		// for JPA
	}

	public SchedulingSettings(long userId, int horizonDays, int minChunkMinutes) {
		this.userId = userId;
		this.horizonDays = horizonDays;
		this.minChunkMinutes = minChunkMinutes;
	}

	public long getUserId() {
		return this.userId;
	}

	public int getHorizonDays() {
		return this.horizonDays;
	}

	public void setHorizonDays(int horizonDays) {
		this.horizonDays = horizonDays;
	}

	public int getMinChunkMinutes() {
		return this.minChunkMinutes;
	}

	public void setMinChunkMinutes(int minChunkMinutes) {
		this.minChunkMinutes = minChunkMinutes;
	}

	public int getBufferMinutes() {
		return this.bufferMinutes;
	}

	public void setBufferMinutes(int bufferMinutes) {
		this.bufferMinutes = bufferMinutes;
	}

	public boolean isOnboarded() {
		return this.onboarded;
	}

	public void markOnboarded() {
		this.onboarded = true;
	}

}
