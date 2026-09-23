package dev.karimbk.reflowtask.user;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the owner of the device can do when someone is locked out, and how a fresh install avoids
 * shipping with a password every copy shares. The exit itself is the caller's; here the runner is
 * driven directly and its outcome (exit code, printed password, database) is checked.
 */
@SpringBootTest
@Transactional
class AdminAccountRunnerTests {

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	@Autowired
	private UserService service;

	@Autowired
	private UserRepository users;

	@Autowired
	private UserSessionRepository sessions;

	@Autowired
	private ConfigurableApplicationContext context;

	private AdminAccountRunner runner(String username, String password, String initialAdmin) {
		return new AdminAccountRunner(this.service, this.context, username, password, initialAdmin);
	}

	private String captureOutput(Runnable action) {
		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured));
		try {
			action.run();
		}
		finally {
			System.setOut(original);
		}
		return captured.toString();
	}

	private User member(String name) {
		return this.users.save(new User(name, this.encoder.encode("old-password-1"), Role.MEMBER, false,
				LocalDateTime.now()));
	}

	@Test
	void resettingWithAGivenPasswordSetsItAndForcesAChange() {
		User user = member("reset-given");

		int code = runner("reset-given", "brand-new-pass", "").reset();

		assertThat(code).isZero();
		User after = this.users.findByUsername("reset-given").orElseThrow();
		assertThat(this.encoder.matches("brand-new-pass", after.getPasswordHash())).isTrue();
		assertThat(this.encoder.matches("old-password-1", after.getPasswordHash())).isFalse();
		assertThat(after.isMustChangePassword()).isTrue();
		assertThat(user.getId()).isEqualTo(after.getId());
	}

	@Test
	void resettingWithoutAPasswordGeneratesOneAndPrintsIt() {
		member("reset-generated");
		int[] code = new int[1];

		String output = captureOutput(() -> code[0] = runner("reset-generated", "", "").reset());

		assertThat(code[0]).isZero();
		String printed = output.lines()
			.filter((line) -> line.startsWith("Temporary password: "))
			.findFirst()
			.orElseThrow()
			.substring("Temporary password: ".length());
		assertThat(printed).hasSize(12);
		User after = this.users.findByUsername("reset-generated").orElseThrow();
		assertThat(this.encoder.matches(printed, after.getPasswordHash())).isTrue();
	}

	@Test
	void theUsernameIsMatchedCaseInsensitively() {
		member("reset-case");

		assertThat(runner("  Reset-CASE ", "brand-new-pass", "").reset()).isZero();
	}

	@Test
	void everySessionOfTheResetAccountEnds() {
		User user = member("reset-sessions");
		this.sessions.save(new UserSession("live-token", user.getId(), LocalDateTime.now(),
				LocalDateTime.now().plusDays(3)));

		runner("reset-sessions", "brand-new-pass", "").reset();

		assertThat(this.sessions.findById("live-token")).isEmpty();
	}

	@Test
	void anUnknownAccountExitsWithOneAndChangesNothing() {
		long before = this.users.count();

		assertThat(runner("nobody-by-that-name", "brand-new-pass", "").reset()).isEqualTo(1);
		assertThat(this.users.count()).isEqualTo(before);
	}

	@Test
	void aTooShortPasswordExitsWithTwoAndLeavesTheOldOne() {
		member("reset-short");

		assertThat(runner("reset-short", "short", "").reset()).isEqualTo(2);

		User after = this.users.findByUsername("reset-short").orElseThrow();
		assertThat(this.encoder.matches("old-password-1", after.getPasswordHash())).isTrue();
	}

	@Test
	void anInitialAdminPasswordReplacesThePlaceholderOnce() {
		assertThat(this.service.replaceSeededAdminPassword("chosen-by-installer")).isTrue();

		User admin = this.users.findById(1L).orElseThrow();
		assertThat(this.encoder.matches("chosen-by-installer", admin.getPasswordHash())).isTrue();
		assertThat(this.encoder.matches("changeme", admin.getPasswordHash())).isFalse();
		assertThat(admin.isMustChangePassword()).isFalse();

		// Once it has been replaced, whether by this or by the person, it is never overwritten again.
		assertThat(this.service.replaceSeededAdminPassword("someone-elses-attempt")).isFalse();
		assertThat(this.encoder.matches("chosen-by-installer", this.users.findById(1L).orElseThrow().getPasswordHash()))
			.isTrue();
	}

	@Test
	void theRunnerAppliesTheInitialAdminPasswordAtStartup() {
		runner("", "", "chosen-by-installer").run(null);

		assertThat(this.encoder.matches("chosen-by-installer", this.users.findById(1L).orElseThrow().getPasswordHash()))
			.isTrue();
	}

	@Test
	void aTooShortInitialAdminPasswordIsIgnored() {
		runner("", "", "short").run(null);

		assertThat(this.encoder.matches("changeme", this.users.findById(1L).orElseThrow().getPasswordHash())).isTrue();
	}

	@Test
	void theSeededHashConstantStillMatchesThePlaceholderPassword() {
		// Guards the constant against drifting from V4__household_users.sql.
		assertThat(this.encoder.matches("changeme", UserService.SEEDED_ADMIN_HASH)).isTrue();
	}

}
