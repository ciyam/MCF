package org.qora.api;

import javax.servlet.http.HttpServletRequest;

import org.qora.globalization.Translator;

public enum ApiExceptionFactory {
	INSTANCE;

	public ApiException createException(HttpServletRequest request, ApiError apiError, Throwable throwable, Object... args) {
		String message = Translator.INSTANCE.translate("ApiError", request.getLocale().getLanguage(), apiError.name(), args);
		return new ApiException(apiError.getStatus(), apiError.getCode(), message, throwable);
	}

	public ApiException createException(HttpServletRequest request, ApiError apiError) {
		return createException(request, apiError, null);
	}

}
