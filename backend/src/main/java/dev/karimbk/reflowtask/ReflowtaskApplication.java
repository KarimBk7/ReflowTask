package dev.karimbk.reflowtask;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReflowtaskApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReflowtaskApplication.class, args);
	}

	/**
	 * Time is injected, never read statically. The scheduler and the reflow job are
	 * entirely about "what time is it now", so tests need to control the clock — and
	 * an inline {@code LocalDateTime.now()} anywhere would make them untestable.
	 */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
