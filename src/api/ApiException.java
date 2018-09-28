package api;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class ApiException extends WebApplicationException {
	// HTTP status code

	int status;

	// API error code
	int error;

	String message;

	public ApiException(int status, int error, String message) {
		this(status, error, message, null);
	}

	public ApiException(int status, int error, String message, Throwable throwable) {
		super(
			message,
			throwable,
			Response.status(Status.fromStatusCode(status))
			.entity(new ApiErrorMessage(error, message))
			.type(MediaType.APPLICATION_JSON)
			.build()
		);

		this.status = status;
		this.error = error;
		this.message = message;
	}
}
