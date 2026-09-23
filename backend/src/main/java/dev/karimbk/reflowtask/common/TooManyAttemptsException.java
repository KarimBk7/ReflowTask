package dev.karimbk.reflowtask.common;

/** Too many failed logins for a username. Translated to a 429 by {@link ApiExceptionHandler}. */
public class TooManyAttemptsException extends RuntimeException {

	public TooManyAttemptsException(String message) {
		super(message);
	}

}
