package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import utils.Base58;

import java.util.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/utils")
@Produces({MediaType.TEXT_PLAIN})
@Tag(name = "Utilities")
public class UtilsResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/base58from64/{base64}")
	@Operation(
		summary = "Convert base64 data to base58",
		responses = {
			@ApiResponse(
				description = "base58 data",
				content = @Content(schema = @Schema(implementation = String.class))
			)
		}
	)
	public String base58from64(@PathParam("base64") String base64) {
		try {
			return Base58.encode(Base64.getDecoder().decode(base64));
		} catch (IllegalArgumentException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}
	}

	@GET
	@Path("/base64from58/{base58}")
	@Operation(
		summary = "Convert base58 data to base64",
		responses = {
			@ApiResponse(
				description = "base64 data",
				content = @Content(schema = @Schema(implementation = String.class))
			)
		}
	)
	public String base64from58(@PathParam("base58") String base58) {
		try {
			return Base64.getEncoder().encodeToString(Base58.decode(base58));
		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}
	}

}
