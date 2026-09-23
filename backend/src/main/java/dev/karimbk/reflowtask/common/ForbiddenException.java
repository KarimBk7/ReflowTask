package dev.karimbk.reflowtask.common;

/** A logged-in user is not allowed to do this. Translated to a 403 by {@link ApiExceptionHandler}. */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}

}
