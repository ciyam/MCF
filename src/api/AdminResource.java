package api;

import globalization.Translator;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "admin")
public class AdminResource {

	@Context
	HttpServletRequest request;

	private static final long startTime = System.currentTimeMillis();

	private ApiErrorFactory apiErrorFactory;

	public AdminResource() {
		this(new ApiErrorFactory(Translator.getInstance()));
	}

	public AdminResource(ApiErrorFactory apiErrorFactory) {
		this.apiErrorFactory = apiErrorFactory;
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
		Security.checkApiCallAllowed("GET admin/uptime", request);

		return Long.toString(System.currentTimeMillis() - startTime);
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
		Security.checkApiCallAllowed("GET admin/stop", request);

		new Thread(new Runnable() {
			@Override
			public void run() {
				Controller.shutdown();
			}
		}); // disabled for now: .start();

		return "false";
	}

}
