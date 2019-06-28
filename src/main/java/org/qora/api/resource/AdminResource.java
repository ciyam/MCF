package org.qora.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.qora.api.Security;
import org.qora.controller.Controller;

@Path("/admin")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Tag(name = "Admin")
public class AdminResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/unused")
	@Parameter(in = ParameterIn.PATH, name = "blockSignature", description = "Block signature", schema = @Schema(type = "string", format = "byte"), example = "very_long_block_signature_in_base58")
	@Parameter(in = ParameterIn.PATH, name = "assetId", description = "Asset ID, 0 is native coin", schema = @Schema(type = "string", format = "byte"))
	@Parameter(in = ParameterIn.PATH, name = "otherAssetId", description = "Asset ID, 0 is native coin", schema = @Schema(type = "string", format = "byte"))
	@Parameter(in = ParameterIn.PATH, name = "address", description = "an account address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	@Parameter(in = ParameterIn.QUERY, name = "count", description = "Maximum number of entries to return, 0 means none", schema = @Schema(type = "integer", defaultValue = "20"))
	@Parameter(in = ParameterIn.QUERY, name = "limit", description = "Maximum number of entries to return, 0 means unlimited", schema = @Schema(type = "integer", defaultValue = "20"))
	@Parameter(in = ParameterIn.QUERY, name = "offset", description = "Starting entry in results, 0 is first entry", schema = @Schema(type = "integer"))
	@Parameter(in = ParameterIn.QUERY, name = "includeTransactions", description = "Include associated transactions in results", schema = @Schema(type = "boolean"))
	@Parameter(in = ParameterIn.QUERY, name = "includeHolders", description = "Include asset holders in results", schema = @Schema(type = "boolean"))
	@Parameter(in = ParameterIn.QUERY, name = "queryAssetId", description = "Asset ID, 0 is native coin", schema = @Schema(type = "string", format = "byte"))
	public String globalParameters() {
		return "";
	}

	@GET
	@Path("/uptime")
	@Operation(
		summary = "Fetch running time of server",
		description = "Returns uptime in milliseconds",
		responses = {
			@ApiResponse(
				description = "uptime in milliseconds",
				content = @Content(schema = @Schema(type = "number"))
			)
		}
	)
	public long uptime() {
		return System.currentTimeMillis() - Controller.startTime;
	}

	@GET
	@Path("/stop")
	@Operation(
		summary = "Shutdown",
		description = "Shutdown",
		responses = {
			@ApiResponse(
				description = "\"true\"",
				content = @Content(schema = @Schema(type = "string"))
			)
		}
	)
	public String shutdown() {
		Security.checkApiCallAllowed(request);

		new Thread(new Runnable() {
			@Override
			public void run() {
				Controller.shutdown();
			}
		}).start();

		return "true";
	}

}
