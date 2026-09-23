package dev.karimbk.reflowtask.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

	void deleteByUserId(Long userId);

}
