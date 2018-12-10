package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
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

import controller.Controller;

@Path("admin")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Extension(name = "translation", properties = {
		@ExtensionProperty(name="path", value="/Api/AdminResource")
	}
)
@Tag(name = "Admin")
public class AdminResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/dud")
	@Parameter(name = "blockSignature", description = "Block signature", schema = @Schema(type = "string", format = "byte", minLength = 84, maxLength=88))
	@Parameter(in = ParameterIn.QUERY, name = "count", description = "Maximum number of entries to return", schema = @Schema(type = "integer", defaultValue = "10"))
	@Parameter(in = ParameterIn.QUERY, name = "limit", description = "Maximum number of entries to return, 0 means unlimited", schema = @Schema(type = "integer", defaultValue = "10"))
	@Parameter(in = ParameterIn.QUERY, name = "offset", description = "Starting entry in results", schema = @Schema(type = "integer"))
	@Parameter(in = ParameterIn.QUERY, name = "includeTransactions", description = "Include associated transactions in results", schema = @Schema(type = "boolean"))
	public String globalParameters() {
		return "";
	}

	@GET
	@Path("/uptime")
	@Operation(
		summary = "Fetch running time of server",
		description = "Returns uptime in milliseconds",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="description.key", value="operation:description")
			})
		},
		responses = {
			@ApiResponse(
				description = "uptime in milliseconds",
				content = @Content(schema = @Schema(implementation = String.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public String uptime() {
		return Long.toString(System.currentTimeMillis() - Controller.startTime);
	}

	@GET
	@Path("/stop")
	@Operation(
		summary = "Shutdown",
		description = "Shutdown",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="description.key", value="operation:description")
			})
		},
		responses = {
			@ApiResponse(
				description = "\"true\"",
				content = @Content(schema = @Schema(implementation = String.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
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
