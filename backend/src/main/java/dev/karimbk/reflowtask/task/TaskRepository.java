package dev.karimbk.reflowtask.task;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

	/**
	 * Everything the scheduler may place for one user. Deliberately unordered: H2 sorts NULLs
	 * first on an ascending column while PostgreSQL sorts them last, so ordering by deadline in
	 * SQL would plan differently in dev than in production. The scheduler applies its own
	 * explicit comparator instead.
	 */
	List<Task> findByUserIdAndStatusNot(long userId, TaskStatus status);

	List<Task> findByUserIdOrderByCreatedAtDesc(long userId);

	Optional<Task> findByIdAndUserId(long id, long userId);

}
