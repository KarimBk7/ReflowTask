package dev.karimbk.reflowtask.config;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedPeriodRepository extends JpaRepository<BlockedPeriod, Long> {

	List<BlockedPeriod> findByUserId(long userId);

	void deleteByUserId(long userId);

}
