package dev.karimbk.reflowtask.config;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, WorkingHoursId> {

	List<WorkingHours> findByUserId(long userId);

	void deleteByUserId(long userId);

}
