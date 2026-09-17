package dev.karimbk.reflowtask.common;

/**
 * A well-formed request the current schedule cannot accept: fixing work on top of something
 * already fixed, moving a completed task's block, or placing work in the past. Translated to a
 * 409 by {@link ApiExceptionHandler}, and kept free of web types like {@link NotFoundException}.
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
