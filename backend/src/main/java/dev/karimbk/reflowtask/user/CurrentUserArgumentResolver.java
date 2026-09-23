package dev.karimbk.reflowtask.user;

import dev.karimbk.reflowtask.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves an {@code @CurrentUser} parameter from the request attribute {@link AuthFilter} sets. */
@Component
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUser.class) && parameter.getParameterType() == User.class;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		Object user = (request != null) ? request.getAttribute(AuthFilter.CURRENT_USER_ATTRIBUTE) : null;
		if (user == null) {
			throw new UnauthorizedException("Log in to continue.");
		}
		return user;
	}

}
