package dev.karimbk.reflowtask.common;

/**
 * A request that is well-formed but cannot be acted on because of what it points to, such as a
 * calendar address that does not return a calendar. Translated to a 400 by {@link ApiExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}
