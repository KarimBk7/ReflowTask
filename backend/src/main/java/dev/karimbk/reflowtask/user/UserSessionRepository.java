package dev.karimbk.reflowtask.user;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

	void deleteByUserId(Long userId);

	void deleteByExpiresAtBefore(LocalDateTime cutoff);

}
