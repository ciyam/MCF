package api;

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
			throw ApiErrorFactory.getInstance().createError(ApiError.UNAUTHORIZED);
		}

		if (!remoteAddr.isLoopbackAddress())
			throw ApiErrorFactory.getInstance().createError(ApiError.UNAUTHORIZED);
	}
}
