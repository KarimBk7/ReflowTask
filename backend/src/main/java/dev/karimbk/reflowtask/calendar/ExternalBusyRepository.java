package dev.karimbk.reflowtask.calendar;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ExternalBusyRepository extends JpaRepository<ExternalBusy, Long> {

	/** Busy time overlapping [from, until) for one person. */
	@Query("select b from ExternalBusy b where b.userId = :userId and b.startAt < :until and b.endAt > :from"
			+ " order by b.startAt")
	List<ExternalBusy> findOverlapping(long userId, LocalDateTime from, LocalDateTime until);

	/** Bulk on purpose: these rows are never loaded as entities that could linger in a session. */
	@Modifying
	@Query("delete from ExternalBusy b where b.sourceId = :sourceId")
	void deleteBySourceId(long sourceId);

}
