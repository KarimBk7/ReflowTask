package dev.karimbk.reflowtask.user;

import java.security.SecureRandom;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Account recovery for whoever owns the device, with no login needed: having a shell on the
 * machine is the credential.
 *
 * <ul>
 * <li>{@code reflowtask.reset-password.username=alice} sets a temporary password for that account
 * (given as {@code reflowtask.reset-password.password}, or generated and printed), forces a
 * change at next login, ends its sessions, and then exits without serving requests. Run it with
 * {@code --spring.main.web-application-type=none}; scripts/reset-password.sh wraps this.</li>
 * <li>{@code reflowtask.initial-admin-password} replaces the shared placeholder password of a fresh
 * install at startup, so the instance never has to be reachable with it.</li>
 * </ul>
 */
@Component
class AdminAccountRunner implements ApplicationRunner {

	private static final Log logger = LogFactory.getLog(AdminAccountRunner.class);

	private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private final UserService users;

	private final ConfigurableApplicationContext context;

	private final String resetUsername;

	private final String resetPassword;

	private final String initialAdminPassword;

	AdminAccountRunner(UserService users, ConfigurableApplicationContext context,
			@Value("${reflowtask.reset-password.username:}") String resetUsername,
			@Value("${reflowtask.reset-password.password:}") String resetPassword,
			@Value("${reflowtask.initial-admin-password:}") String initialAdminPassword) {
		this.users = users;
		this.context = context;
		this.resetUsername = resetUsername;
		this.resetPassword = resetPassword;
		this.initialAdminPassword = initialAdminPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!this.resetUsername.isBlank()) {
			int code = reset();
			System.exit(SpringApplication.exit(this.context, () -> code));
			return;
		}
		if (this.initialAdminPassword.length() >= 8 && this.users.replaceSeededAdminPassword(this.initialAdminPassword)) {
			logger.info("The admin account now uses the password from REFLOWTASK_INITIAL_ADMIN_PASSWORD.");
		}
	}

	/** Returns the process exit code: 0 done, 1 no such account, 2 password too short. */
	int reset() {
		String temporary = this.resetPassword.isBlank() ? generate() : this.resetPassword;
		int code = 0;
		if (temporary.length() < 8) {
			System.err.println("The temporary password must be at least 8 characters.");
			code = 2;
		}
		else {
			try {
				this.users.resetPasswordOf(this.resetUsername, temporary);
				System.out.println();
				System.out.println("Password reset for '" + UserService.normalize(this.resetUsername) + "'.");
				System.out.println("Temporary password: " + temporary);
				System.out.println("They must choose a new one at next login; all their sessions were ended.");
			}
			catch (RuntimeException ex) {
				System.err.println("Could not reset: " + ex.getMessage());
				code = 1;
			}
		}
		return code;
	}

	private static String generate() {
		SecureRandom random = new SecureRandom();
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 12; i++) {
			builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return builder.toString();
	}

}
