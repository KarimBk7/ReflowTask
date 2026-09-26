package dev.karimbk.reflowtask.calendar;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import dev.karimbk.reflowtask.common.BadRequestException;
import dev.karimbk.reflowtask.common.NotFoundException;
import dev.karimbk.reflowtask.config.SchedulingConfigProvider;
import dev.karimbk.reflowtask.schedule.RescheduleTrigger;
import dev.karimbk.reflowtask.schedule.SchedulerService;
import dev.karimbk.reflowtask.schedule.TimeBlockRepository;
import dev.karimbk.reflowtask.user.User;
import dev.karimbk.reflowtask.user.UserRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendar sync, both ways: a subscription feed of one's own planned blocks, and other calendars
 * read in as busy time the scheduler plans around.
 */
@Service
public class CalendarService {

	private static final Log logger = LogFactory.getLog(CalendarService.class);

	/** How far back the feed reaches, so today's and recent work still shows in the calendar app. */
	private static final int FEED_DAYS_BACK = 30;

	private final UserRepository users;

	private final CalendarSourceRepository sources;

	private final ExternalBusyRepository busy;

	private final TimeBlockRepository blocks;

	private final SchedulerService scheduler;

	private final SchedulingConfigProvider config;

	private final CalendarFetcher fetcher;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	CalendarService(UserRepository users, CalendarSourceRepository sources, ExternalBusyRepository busy,
			TimeBlockRepository blocks, SchedulerService scheduler, SchedulingConfigProvider config,
			CalendarFetcher fetcher, Clock clock) {
		this.users = users;
		this.sources = sources;
		this.busy = busy;
		this.blocks = blocks;
		this.scheduler = scheduler;
		this.config = config;
		this.fetcher = fetcher;
		this.clock = clock;
	}

	// --- out: the subscription feed ---------------------------------------------------

	/**
	 * Turns the feed on with a new secret, or replaces the secret: the old link stops working at
	 * once, which is the remedy for a link that was shared by mistake.
	 */
	@Transactional
	public String renewFeedToken(User user) {
		byte[] bytes = new byte[32];
		this.random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		// The user arrives detached (see UserService.changePassword), so it is saved explicitly.
		user.setCalendarToken(token);
		this.users.save(user);
		return token;
	}

	@Transactional
	public void disableFeed(User user) {
		user.setCalendarToken(null);
		this.users.save(user);
	}

	/** The feed for a secret, or empty when no one has that secret. */
	@Transactional(readOnly = true)
	public Optional<String> feed(String token) {
		return this.users.findByCalendarToken(token).map((owner) -> {
			LocalDateTime now = LocalDateTime.now(this.clock);
			LocalDateTime since = now.minusDays(FEED_DAYS_BACK);
			return IcsWriter.feed("ReflowTask (" + owner.getUsername() + ")",
					this.blocks.findAllWithTaskByUserId(owner.getId())
						.stream()
						.filter((block) -> block.getEndAt().isAfter(since))
						.sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
						.toList(),
					now, this.clock.getZone());
		});
	}

	// --- in: other calendars as busy time -----------------------------------------------

	@Transactional(readOnly = true)
	public List<CalendarSource> sources(long userId) {
		return this.sources.findByUserIdOrderByName(userId);
	}

	@Transactional(readOnly = true)
	public List<ExternalBusy> busy(long userId, LocalDateTime from, LocalDateTime until) {
		return this.busy.findOverlapping(userId, from, until);
	}

	/**
	 * Adds another calendar. It is read straight away, and only kept when that works, so a typo
	 * is told immediately instead of turning into a silently empty calendar.
	 */
	@Transactional
	public CalendarSource add(long userId, String name, String address) {
		// Kept as typed: a webcal:// address is tried as https and then http on every read.
		String url = address.strip();
		CalendarFetcher.normalize(url);
		List<IcsReader.Busy> read = read(userId, url);
		CalendarSource source = this.sources.save(new CalendarSource(userId, name.strip(), url));
		store(source, read);
		this.scheduler.replan(userId, RescheduleTrigger.CALENDAR_SYNCED);
		return source;
	}

	@Transactional
	public void remove(long userId, long sourceId) {
		CalendarSource source = this.sources.findByIdAndUserId(sourceId, userId)
			.orElseThrow(() -> new NotFoundException("Calendar", sourceId));
		this.busy.deleteBySourceId(source.getId());
		this.sources.delete(source);
		this.sources.flush();
		this.scheduler.replan(userId, RescheduleTrigger.CALENDAR_SYNCED);
	}

	/** Reads every calendar of one person again, then replans. For the refresh button. */
	@Transactional
	public void refreshAndReplan(long userId) {
		refresh(userId);
		this.scheduler.replan(userId, RescheduleTrigger.CALENDAR_SYNCED);
	}

	/**
	 * Reads every calendar of one person again without replanning, for the hourly job, which
	 * replans right after. A calendar that cannot be read keeps its last known busy time and says
	 * why, rather than suddenly freeing hours that are probably still taken.
	 */
	@Transactional
	public void refresh(long userId) {
		for (CalendarSource source : this.sources.findByUserIdOrderByName(userId)) {
			try {
				store(source, read(userId, source.getUrl()));
			}
			catch (BadRequestException ex) {
				source.failed(ex.getMessage());
				logger.warn("Calendar '" + source.getName() + "' of user " + userId + " could not be read: "
						+ ex.getMessage());
			}
		}
	}

	private List<IcsReader.Busy> read(long userId, String url) {
		LocalDateTime now = LocalDateTime.now(this.clock);
		int horizon = this.config.current(userId).horizonDays();
		try {
			return IcsReader.busyIn(this.fetcher.fetch(url), now.minusDays(1), now.plusDays(horizon + 2),
					this.clock.getZone());
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
	}

	private void store(CalendarSource source, List<IcsReader.Busy> read) {
		this.busy.deleteBySourceId(source.getId());
		this.busy.saveAll(read.stream()
			.map((interval) -> new ExternalBusy(source.getId(), source.getUserId(), interval.start(), interval.end(),
					interval.title()))
			.toList());
		source.fetched(LocalDateTime.now(this.clock));
		this.sources.save(source);
	}

}
