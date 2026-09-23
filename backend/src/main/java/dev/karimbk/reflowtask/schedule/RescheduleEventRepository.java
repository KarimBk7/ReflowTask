package dev.karimbk.reflowtask.schedule;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RescheduleEventRepository extends JpaRepository<RescheduleEvent, Long> {

	List<RescheduleEvent> findByUserIdOrderByOccurredAtDesc(long userId, Limit limit);

}
