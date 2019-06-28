package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import transform.TransformationException;
import transform.transaction.RegisterNameTransactionTransformer;
import utils.Base58;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import data.transaction.RegisterNameTransactionData;

@Path("/names")
@Produces({
	MediaType.TEXT_PLAIN
})
@Tag(
	name = "Names"
)
public class NamesResource {

	@Context
	HttpServletRequest request;

	@POST
	@Path("/register")
	@Operation(
		summary = "Build raw, unsigned REGISTER_NAME transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = RegisterNameTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned REGISTER_NAME transaction encoded in Base58",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String buildTransaction(RegisterNameTransactionData transactionData) {
		try {
			byte[] bytes = RegisterNameTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.UNKNOWN, e);
		}
	}

}