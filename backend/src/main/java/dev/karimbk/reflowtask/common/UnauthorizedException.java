package dev.karimbk.reflowtask.common;

/**
 * No valid session: missing, expired or invalid cookie, or a bad login attempt. Translated to a
 * 401 by {@link ApiExceptionHandler}.
 */
public class UnauthorizedException extends RuntimeException {

	public UnauthorizedException(String message) {
		super(message);
	}

}
