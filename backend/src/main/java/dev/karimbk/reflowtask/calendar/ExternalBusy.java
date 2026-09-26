package dev.karimbk.reflowtask.calendar;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One event read from another calendar: time the scheduler must leave alone. */
@Entity
@Table(name = "external_busy")
public class ExternalBusy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long sourceId;

	@Column(nullable = false)
	private long userId;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	private String title;

	protected ExternalBusy() {
		// for JPA
	}

	public ExternalBusy(long sourceId, long userId, LocalDateTime startAt, LocalDateTime endAt, String title) {
		this.sourceId = sourceId;
		this.userId = userId;
		this.startAt = startAt;
		this.endAt = endAt;
		this.title = title;
	}

	public long getSourceId() {
		return this.sourceId;
	}

	public LocalDateTime getStartAt() {
		return this.startAt;
	}

	public LocalDateTime getEndAt() {
		return this.endAt;
	}

	public String getTitle() {
		return this.title;
	}

}
