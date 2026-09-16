package dev.karimbk.reflowtask.config;

import dev.karimbk.reflowtask.config.ConfigPayloads.Config;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The scheduling configuration: when work may happen, what is blocked, how far ahead to plan.
 *
 * Reading it lets the week view draw the window the scheduler actually planned against, so the
 * board cannot quietly disagree with its own plan. Writing it replaces the whole configuration
 * at once and replans, because a half-applied configuration is a schedule nobody asked for.
 */
@RestController
@RequestMapping("/api/v1/config")
class ConfigController {

	private final ConfigService service;

	ConfigController(ConfigService service) {
		this.service = service;
	}

	@GetMapping
	Config current() {
		return this.service.current();
	}

	@PutMapping
	Config replace(@Valid @RequestBody Config config) {
		return this.service.replace(config);
	}

}
