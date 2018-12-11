package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import api.models.AssetWithHolders;
import data.assets.AssetData;

@Path("/assets")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Tag(name = "Assets")
public class AssetsResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/all")
	@Operation(
		summary = "List all known assets",
		responses = {
			@ApiResponse(
				description = "asset info",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = AssetData.class)))
			)
		}
	)
	public List<AssetData> getAllAssets() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getAssetRepository().getAllAssets();
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/info")
	@Operation(
		summary = "Info on specific asset",
		responses = {
			@ApiResponse(
				description = "asset info",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = AssetData.class)))
			)
		}
	)
	public AssetWithHolders getAssetInfo(@QueryParam("key") Integer key, @QueryParam("name") String name, @Parameter(ref = "includeHolders") @QueryParam("withHolders") boolean includeHolders) {
		if (key == null && (name == null || name.isEmpty()))
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_CRITERIA);

		try (final Repository repository = RepositoryManager.getRepository()) {
			AssetData assetData = null;

			if (key != null)
				assetData = repository.getAssetRepository().fromAssetId(key);
			else
				assetData = repository.getAssetRepository().fromAssetName(name);

			if (assetData == null)
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_ASSET_ID);

			return new AssetWithHolders(repository, assetData, includeHolders);
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

}
