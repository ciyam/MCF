package org.qora.api;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletRequest;

public class Security {

	// TODO: replace with proper authentication
	public static void checkApiCallAllowed(HttpServletRequest request) {
		InetAddress remoteAddr;
		try {
			remoteAddr = InetAddress.getByName(request.getRemoteAddr());
		} catch (UnknownHostException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.UNAUTHORIZED);
		}

		if (!remoteAddr.isLoopbackAddress())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.UNAUTHORIZED);
	}
}
