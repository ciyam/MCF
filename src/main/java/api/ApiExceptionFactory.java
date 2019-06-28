package api;

import javax.servlet.http.HttpServletRequest;

import globalization.Translator;

public enum ApiExceptionFactory {
	INSTANCE;

	public ApiException createException(HttpServletRequest request, ApiError apiError, Throwable throwable, Object... args) {
		String template = Translator.INSTANCE.translate("ApiError", request.getLocale().getLanguage(), apiError.name());
		String message = String.format(template, args);
		return new ApiException(apiError.getStatus(), apiError.getCode(), message, throwable);
	}

	public ApiException createException(HttpServletRequest request, ApiError apiError) {
		return createException(request, apiError, null);
	}

}
