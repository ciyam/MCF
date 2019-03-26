package org.qora.api.resource;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<RuntimeException> {

	private static final Logger LOGGER = LogManager.getLogger(ApiExceptionMapper.class);

	@Context
	private HttpServletRequest request;

	@Context
	private ServletContext servletContext;

	@Override
	public Response toResponse(RuntimeException e) {
		LOGGER.info(String.format("Exception %s during API call: %s", e.getClass().getCanonicalName(), request.getRequestURI()));

		if (e instanceof WebApplicationException)
			return ((WebApplicationException) e).getResponse();

		return Response.serverError().build();
	}

}
