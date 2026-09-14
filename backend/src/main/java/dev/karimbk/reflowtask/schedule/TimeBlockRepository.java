package dev.karimbk.reflowtask.schedule;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {

	/** Fetches the task alongside each block: replanning reads every block's task status. */
	@Query("select b from TimeBlock b join fetch b.task")
	List<TimeBlock> findAllWithTask();

	@Query("select b from TimeBlock b join fetch b.task where b.startAt < :until and b.endAt > :from")
	List<TimeBlock> findOverlapping(LocalDateTime from, LocalDateTime until);

	/**
	 * Loaded rather than bulk-deleted on purpose. A {@code @Modifying} delete would not
	 * update the persistence context, leaving these blocks in the session still pointing at
	 * a task that has been removed — and the next flush would then try to persist one.
	 */
	List<TimeBlock> findByTaskId(long taskId);

}
