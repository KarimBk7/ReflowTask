package dev.karimbk.reflowtask.user;

import java.net.URI;
import java.util.List;

import dev.karimbk.reflowtask.user.UserPayloads.Credentials;
import dev.karimbk.reflowtask.user.UserPayloads.NewPassword;
import dev.karimbk.reflowtask.user.UserPayloads.StatusResponse;
import dev.karimbk.reflowtask.user.UserPayloads.UserResponse;
import dev.karimbk.reflowtask.user.UserService.LoginResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class UserController {

	private final UserService service;

	UserController(UserService service) {
		this.service = service;
	}

	@GetMapping("/auth/status")
	StatusResponse status() {
		return new StatusResponse(this.service.needsBootstrap());
	}

	@PostMapping("/auth/bootstrap")
	UserResponse bootstrap(@Valid @RequestBody Credentials credentials, HttpServletResponse response) {
		LoginResult result = this.service.bootstrap(credentials.username(), credentials.password());
		response.addCookie(result.cookie());
		return UserResponse.of(result.user());
	}

	@PostMapping("/auth/login")
	UserResponse login(@Valid @RequestBody Credentials credentials, HttpServletResponse response) {
		LoginResult result = this.service.login(credentials.username(), credentials.password());
		response.addCookie(result.cookie());
		return UserResponse.of(result.user());
	}

	@PostMapping("/auth/logout")
	ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		response.addCookie(this.service.logout(sessionTokenOf(request)));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/auth/me")
	UserResponse me(@CurrentUser User user) {
		return UserResponse.of(user);
	}

	@PostMapping("/auth/change-password")
	UserResponse changePassword(@CurrentUser User user, @Valid @RequestBody NewPassword request) {
		this.service.changePassword(user, request.password());
		return UserResponse.of(user);
	}

	@GetMapping("/users")
	List<UserResponse> list(@CurrentUser User user) {
		this.service.requireAdmin(user);
		return this.service.listAll().stream().map(UserResponse::of).toList();
	}

	@PostMapping("/users")
	ResponseEntity<UserResponse> create(@CurrentUser User user, @Valid @RequestBody Credentials credentials) {
		this.service.requireAdmin(user);
		UserResponse created = UserResponse
			.of(this.service.createMember(credentials.username(), credentials.password()));
		return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
	}

	@DeleteMapping("/users/{id}")
	ResponseEntity<Void> delete(@CurrentUser User user, @PathVariable long id) {
		this.service.requireAdmin(user);
		this.service.deleteMember(user, id);
		return ResponseEntity.noContent().build();
	}

	private String sessionTokenOf(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (AuthFilter.SESSION_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

}
