package dev.karimbk.reflowtask.calendar;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarSourceRepository extends JpaRepository<CalendarSource, Long> {

	List<CalendarSource> findByUserIdOrderByName(long userId);

	Optional<CalendarSource> findByIdAndUserId(long id, long userId);

}
