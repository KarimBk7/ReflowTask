package dev.karimbk.reflowtask.calendar;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import dev.karimbk.reflowtask.user.CurrentUser;
import dev.karimbk.reflowtask.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar")
class CalendarController {

	private static final String FEED_PATH = "/api/v1/calendar/feed/";

	private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

	private final CalendarService service;

	CalendarController(CalendarService service) {
		this.service = service;
	}

	/** Where the subscription feed lives; the browser adds its own host to make it a full link. */
	record FeedResponse(String path) {

		static FeedResponse of(String token) {
			return new FeedResponse(token == null ? null : FEED_PATH + token + ".ics");
		}

	}

	@GetMapping("/feed")
	FeedResponse feed(@CurrentUser User user) {
		return FeedResponse.of(user.getCalendarToken());
	}

	@PostMapping("/feed")
	FeedResponse renewFeed(@CurrentUser User user) {
		return FeedResponse.of(this.service.renewFeedToken(user));
	}

	@DeleteMapping("/feed")
	ResponseEntity<Void> disableFeed(@CurrentUser User user) {
		this.service.disableFeed(user);
		return ResponseEntity.noContent().build();
	}

	/**
	 * The feed itself. Calendar apps cannot log in, so the secret in the address is the whole
	 * credential; an unknown one is a plain 404.
	 */
	@GetMapping("/feed/{token}.ics")
	ResponseEntity<String> ics(@PathVariable String token) {
		return this.service.feed(token)
			.map((body) -> ResponseEntity.ok()
				.contentType(TEXT_CALENDAR)
				.cacheControl(CacheControl.noStore())
				.header("Content-Disposition", "inline; filename=\"reflowtask.ics\"")
				.body(body))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	record SourceView(long id, String name, String url, LocalDateTime lastFetchedAt, String lastError) {

		static SourceView of(CalendarSource source) {
			return new SourceView(source.getId(), source.getName(), source.getUrl(), source.getLastFetchedAt(),
					source.getLastError());
		}

	}

	record NewSource(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 2000) String url) {
	}

	@GetMapping("/sources")
	List<SourceView> sources(@CurrentUser User user) {
		return this.service.sources(user.getId()).stream().map(SourceView::of).toList();
	}

	@PostMapping("/sources")
	ResponseEntity<SourceView> add(@CurrentUser User user, @Valid @RequestBody NewSource request) {
		SourceView created = SourceView.of(this.service.add(user.getId(), request.name(), request.url()));
		return ResponseEntity.created(URI.create("/api/v1/calendar/sources/" + created.id())).body(created);
	}

	@DeleteMapping("/sources/{id}")
	ResponseEntity<Void> remove(@CurrentUser User user, @PathVariable long id) {
		this.service.remove(user.getId(), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/sources/refresh")
	List<SourceView> refresh(@CurrentUser User user) {
		this.service.refreshAndReplan(user.getId());
		return sources(user);
	}

	record BusyView(LocalDateTime startAt, LocalDateTime endAt, String title, long sourceId) {
	}

	@GetMapping("/busy")
	List<BusyView> busy(@CurrentUser User user,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
		return this.service.busy(user.getId(), from, to)
			.stream()
			.map((interval) -> new BusyView(interval.getStartAt(), interval.getEndAt(), interval.getTitle(),
					interval.getSourceId()))
			.toList();
	}

}
