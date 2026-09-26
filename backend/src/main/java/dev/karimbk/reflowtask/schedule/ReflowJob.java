package dev.karimbk.reflowtask.schedule;

import dev.karimbk.reflowtask.calendar.CalendarService;
import dev.karimbk.reflowtask.user.UserRepository;
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

	private final UserRepository users;

	private final CalendarService calendars;

	ReflowJob(SchedulerService scheduler, UserRepository users, CalendarService calendars) {
		this.scheduler = scheduler;
		this.users = users;
		this.calendars = calendars;
	}

	@Scheduled(cron = "${reflowtask.reflow.cron}")
	void reflow() {
		// ponytail: sequential loop over users, fine for a household-sized user count.
		for (long userId : this.users.findAllIds()) {
			try {
				// Other calendars first, so the replan sees today's appointments, not yesterday's.
				this.calendars.refresh(userId);
				this.scheduler.replan(userId, RescheduleTrigger.SCHEDULED_JOB)
					.ifPresent((recorded) -> logger.info("Schedule reflowed for user " + userId + ": "
							+ recorded.getSummary()));
			}
			catch (RuntimeException ex) {
				// One user's failure must not stop another's reflow.
				logger.error("Reflow failed for user " + userId, ex);
			}
		}
	}

}
