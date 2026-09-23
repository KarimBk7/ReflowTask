package dev.karimbk.reflowtask.user;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter as the logged-in user resolved from the session cookie
 * by {@link AuthFilter}. Resolving to no user throws {@link dev.karimbk.reflowtask.common.UnauthorizedException},
 * so any endpoint taking this parameter requires a valid session by construction.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {

}
