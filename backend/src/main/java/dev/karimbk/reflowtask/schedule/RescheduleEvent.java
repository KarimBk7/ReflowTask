package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * A record that the schedule changed, and how.
 *
 * This exists because PRODUCT.md forbids silent schedule mutation: a plan that rearranges
 * itself with no evidence is untrustworthy. Events are only written when something actually
 * moved, so the history never fills up with "nothing happened".
 */
@Entity
@Table(name = "reschedule_event")
public class RescheduleEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDateTime occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false)
	private RescheduleTrigger triggerType;

	private String summary;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "event_id", nullable = false)
	private List<RescheduleEventItem> items = new ArrayList<>();

	protected RescheduleEvent() {
		// for JPA
	}

	public RescheduleEvent(LocalDateTime occurredAt, RescheduleTrigger triggerType, String summary) {
		this.occurredAt = occurredAt;
		this.triggerType = triggerType;
		this.summary = summary;
	}

	public void add(RescheduleEventItem item) {
		this.items.add(item);
	}

	public Long getId() {
		return this.id;
	}

	public LocalDateTime getOccurredAt() {
		return this.occurredAt;
	}

	public RescheduleTrigger getTriggerType() {
		return this.triggerType;
	}

	public String getSummary() {
		return this.summary;
	}

	public List<RescheduleEventItem> getItems() {
		return Collections.unmodifiableList(this.items);
	}

}
