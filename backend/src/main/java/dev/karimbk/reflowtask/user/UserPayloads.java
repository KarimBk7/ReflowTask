package dev.karimbk.reflowtask.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class UserPayloads {

	static final String USERNAME_RULE = "^[A-Za-z0-9._-]{2,50}$";

	static final String USERNAME_MESSAGE = "must be 2-50 letters, digits, dots, dashes or underscores";

	static final String PASSWORD_MESSAGE = "must be at least 8 characters";

	private UserPayloads() {
	}

	/** Login accepts whatever was typed: a too-short password is simply wrong, not a validation error. */
	record LoginRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 100) String password) {
	}

	record NewAccount(@NotBlank @Pattern(regexp = USERNAME_RULE, message = USERNAME_MESSAGE) String username,
			@NotBlank @Size(min = 8, max = 100, message = PASSWORD_MESSAGE) String password, Role role) {
	}

	/** The current password is required for a voluntary change, and skipped for a forced first one. */
	record NewPassword(@Size(max = 100) String currentPassword,
			@NotBlank @Size(min = 8, max = 100, message = PASSWORD_MESSAGE) String password) {
	}

	record ResetPassword(@NotBlank @Size(min = 8, max = 100, message = PASSWORD_MESSAGE) String password) {
	}

	record UserResponse(long id, String username, Role role, boolean mustChangePassword) {

		static UserResponse of(User user) {
			return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isMustChangePassword());
		}

	}

}
