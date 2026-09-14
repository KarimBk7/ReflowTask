package dev.karimbk.reflowtask.schedule;

import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The reason this product needs a server at all: the schedule repairs itself on a clock that
 * does not care whether any particular device is switched on.
 *
 * The cron expression is configurable, and setting it to "-" disables the job — which is how
 * tests keep it from firing while they drive replanning directly.
 */
@Component
class ReflowJob {

	private static final Log logger = LogFactory.getLog(ReflowJob.class);

	private final SchedulerService scheduler;

	ReflowJob(SchedulerService scheduler) {
		this.scheduler = scheduler;
	}

	@Scheduled(cron = "${reflowtask.reflow.cron}")
	void reflow() {
		Optional<RescheduleEvent> event = this.scheduler.replan(RescheduleTrigger.SCHEDULED_JOB);
		event.ifPresent((recorded) -> logger.info("Schedule reflowed: " + recorded.getSummary()));
	}

}
