package dev.karimbk.reflowtask.common;

/**
 * Domain-level "no such thing", translated to a 404 by {@link ApiExceptionHandler}. Kept
 * free of web types so services do not depend on the HTTP layer.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String what, Object id) {
		super(what + " " + id + " not found");
	}

}
