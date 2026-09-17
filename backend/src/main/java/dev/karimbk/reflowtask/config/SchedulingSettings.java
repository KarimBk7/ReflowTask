package dev.karimbk.reflowtask.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Single-row settings table; the row id is fixed at 1 and enforced by a CHECK constraint. */
@Entity
@Table(name = "scheduling_settings")
public class SchedulingSettings {

	public static final short SINGLETON_ID = 1;

	@Id
	private short id = SINGLETON_ID;

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

	public SchedulingSettings(int horizonDays, int minChunkMinutes) {
		this.horizonDays = horizonDays;
		this.minChunkMinutes = minChunkMinutes;
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
