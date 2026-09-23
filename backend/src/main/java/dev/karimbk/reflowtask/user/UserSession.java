package dev.karimbk.reflowtask.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A logged-in session. Its {@code id} is the opaque token the cookie carries. */
@Entity
@Table(name = "user_session")
public class UserSession {

	@Id
	private String id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	protected UserSession() {
		// for JPA
	}

	public UserSession(String id, Long userId, LocalDateTime createdAt, LocalDateTime expiresAt) {
		this.id = id;
		this.userId = userId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public String getId() {
		return this.id;
	}

	public Long getUserId() {
		return this.userId;
	}

	public LocalDateTime getExpiresAt() {
		return this.expiresAt;
	}

	public boolean isExpired(LocalDateTime now) {
		return !this.expiresAt.isAfter(now);
	}

}
