package org.qora.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.qora.api.ApiError;
import org.qora.api.ApiErrors;
import org.qora.api.ApiExceptionFactory;
import org.qora.api.Security;
import org.qora.api.model.ActivitySummary;
import org.qora.controller.Controller;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;

@Path("/admin")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Tag(name = "Admin")
public class AdminResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/unused")
	@Parameter(in = ParameterIn.PATH, name = "assetid", description = "Asset ID, 0 is native coin", schema = @Schema(type = "integer"))
	@Parameter(in = ParameterIn.PATH, name = "otherassetid", description = "Asset ID, 0 is native coin", schema = @Schema(type = "integer"))
	@Parameter(in = ParameterIn.PATH, name = "address", description = "an account address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	@Parameter(in = ParameterIn.QUERY, name = "count", description = "Maximum number of entries to return, 0 means none", schema = @Schema(type = "integer", defaultValue = "20"))
	@Parameter(in = ParameterIn.QUERY, name = "limit", description = "Maximum number of entries to return, 0 means unlimited", schema = @Schema(type = "integer", defaultValue = "20"))
	@Parameter(in = ParameterIn.QUERY, name = "offset", description = "Starting entry in results, 0 is first entry", schema = @Schema(type = "integer"))
	@Parameter(in = ParameterIn.QUERY, name = "reverse", description = "Reverse results", schema = @Schema(type = "boolean"))
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
				content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "number"))
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
				content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string"))
			)
		}
	)
	public String shutdown() {
		Security.checkApiCallAllowed(request);

		new Thread(new Runnable() {
			@Override
			public void run() {
				// Short sleep to allow HTTP response body to be emitted
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// Not important
				}

				Controller.getInstance().shutdownAndExit();
			}
		}).start();

		return "true";
	}

	@GET
	@Path("/summary")
	@Operation(
		summary = "Summary of activity since midnight, UTC",
		responses = {
			@ApiResponse(
				content = @Content(schema = @Schema(implementation = ActivitySummary.class))
			)
		}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public ActivitySummary summary() {
		ActivitySummary summary = new ActivitySummary();

		LocalDate date = LocalDate.now();
		LocalTime time = LocalTime.of(0, 0);
		ZoneOffset offset = ZoneOffset.UTC;
		long start = OffsetDateTime.of(date, time, offset).toInstant().toEpochMilli();

		try (final Repository repository = RepositoryManager.getRepository()) {
			int startHeight = repository.getBlockRepository().getHeightFromTimestamp(start);
			int endHeight = repository.getBlockRepository().getBlockchainHeight();

			summary.blockCount = endHeight - startHeight;

			summary.transactionCountByType = repository.getTransactionRepository().getTransactionSummary(startHeight + 1, endHeight);

			for (Integer count : summary.transactionCountByType.values())
				summary.transactionCount += count;

			summary.assetsIssued = repository.getAssetRepository().getRecentAssetIds(start).size();

			summary.namesRegistered = repository.getNameRepository().getRecentNames(start).size();

			return summary;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}

	}

}
