package dev.karimbk.reflowtask.calendar;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Another calendar, read by URL, whose events block time in this person's schedule. */
@Entity
@Table(name = "calendar_source")
public class CalendarSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long userId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String url;

	private LocalDateTime lastFetchedAt;

	private String lastError;

	protected CalendarSource() {
		// for JPA
	}

	public CalendarSource(long userId, String name, String url) {
		this.userId = userId;
		this.name = name;
		this.url = url;
	}

	public Long getId() {
		return this.id;
	}

	public long getUserId() {
		return this.userId;
	}

	public String getName() {
		return this.name;
	}

	public String getUrl() {
		return this.url;
	}

	public LocalDateTime getLastFetchedAt() {
		return this.lastFetchedAt;
	}

	public String getLastError() {
		return this.lastError;
	}

	void fetched(LocalDateTime at) {
		this.lastFetchedAt = at;
		this.lastError = null;
	}

	void failed(String error) {
		this.lastError = error.length() > 500 ? error.substring(0, 500) : error;
	}

}
