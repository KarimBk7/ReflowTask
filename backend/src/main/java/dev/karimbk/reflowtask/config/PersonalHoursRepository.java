package dev.karimbk.reflowtask.config;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalHoursRepository extends JpaRepository<PersonalHours, WorkingHoursId> {

	List<PersonalHours> findByUserId(long userId);

	void deleteByUserId(long userId);

}
