package dev.karimbk.reflowtask.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);

	List<User> findAllByOrderByUsername();

	long countByRole(Role role);

	@Query("select u.id from User u")
	List<Long> findAllIds();

}
