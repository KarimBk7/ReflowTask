package dev.karimbk.reflowtask.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class UserPayloads {

	private UserPayloads() {
	}

	record StatusResponse(boolean needsBootstrap) {
	}

	record Credentials(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 100) String password) {
	}

	record NewPassword(@NotBlank @Size(max = 100) String password) {
	}

	record UserResponse(long id, String username, Role role, boolean mustChangePassword) {

		static UserResponse of(User user) {
			return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isMustChangePassword());
		}

	}

}
