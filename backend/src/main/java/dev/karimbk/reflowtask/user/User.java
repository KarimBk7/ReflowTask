package dev.karimbk.reflowtask.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One household member. A LAN-only household login, not a full account system. */
@Entity
@Table(name = "app_user")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String username;

	@Column(nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private boolean mustChangePassword;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected User() {
		// for JPA
	}

	public User(String username, String passwordHash, Role role, boolean mustChangePassword,
			LocalDateTime createdAt) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
		this.mustChangePassword = mustChangePassword;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return this.id;
	}

	public String getUsername() {
		return this.username;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public Role getRole() {
		return this.role;
	}

	public boolean isAdmin() {
		return this.role == Role.ADMIN;
	}

	public boolean isMustChangePassword() {
		return this.mustChangePassword;
	}

	public void setMustChangePassword(boolean mustChangePassword) {
		this.mustChangePassword = mustChangePassword;
	}

	public LocalDateTime getCreatedAt() {
		return this.createdAt;
	}

}
