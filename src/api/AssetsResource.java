package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import api.models.AssetWithHolders;
import api.models.IssueAssetRequest;
import api.models.TradeWithOrderInfo;
import data.assets.AssetData;
import data.assets.OrderData;
import data.assets.TradeData;

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
	public List<AssetData> getAllAssets(@Parameter(ref = "limit") @QueryParam("limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<AssetData> assets = repository.getAssetRepository().getAllAssets();

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, assets.size());
			int toIndex = limit == 0 ? assets.size() : Integer.min(fromIndex + limit, assets.size());
			assets = assets.subList(fromIndex, toIndex);

			return assets;
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/info")
	@Operation(
		summary = "Info on specific asset",
		description = "Supply either assetId OR assetName. (If both supplied, assetId takes priority).",
		responses = {
			@ApiResponse(
				description = "asset info",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = AssetWithHolders.class)))
			)
		}
	)
	public AssetWithHolders getAssetInfo(@QueryParam("assetId") Integer assetId, @QueryParam("assetName") String assetName, @Parameter(ref = "includeHolders") @QueryParam("withHolders") boolean includeHolders) {
		if (assetId == null && (assetName == null || assetName.isEmpty()))
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_CRITERIA);

		try (final Repository repository = RepositoryManager.getRepository()) {
			AssetData assetData = null;

			if (assetId != null)
				assetData = repository.getAssetRepository().fromAssetId(assetId);
			else
				assetData = repository.getAssetRepository().fromAssetName(assetName);

			if (assetData == null)
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_ASSET_ID);

			return new AssetWithHolders(repository, assetData, includeHolders);
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/orderbook/{assetId}/{otherAssetId}")
	@Operation(
		summary = "Asset order book",
		description = "Returns open orders, offering {assetId} for {otherAssetId} in return.",
		responses = {
			@ApiResponse(
				description = "asset orders",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderData.class)))
			)
		}
	)
	public List<OrderData> getAssetOrders(@Parameter(ref = "assetId") @PathParam("assetId") int assetId, @Parameter(ref = "otherAssetId") @PathParam("otherAssetId") int otherAssetId,
			@Parameter(ref = "limit") @QueryParam("limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			if (!repository.getAssetRepository().assetExists(assetId))
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_ASSET_ID);

			if (!repository.getAssetRepository().assetExists(otherAssetId))
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_ASSET_ID);

			List<OrderData> orders = repository.getAssetRepository().getOpenOrders(assetId, otherAssetId);

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, orders.size());
			int toIndex = limit == 0 ? orders.size() : Integer.min(fromIndex + limit, orders.size());
			orders = orders.subList(fromIndex, toIndex);

			return orders;
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/trades/{assetId}/{otherAssetId}")
	@Operation(
		summary = "Asset trades",
		description = "Returns successful trades of {assetId} for {otherAssetId}.<br>" +
						"Does NOT include trades of {otherAssetId} for {assetId}!<br>" +
						"\"Initiating\" order is the order that caused the actual trade by matching up with the \"target\" order.",
		responses = {
			@ApiResponse(
				description = "asset trades",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = TradeWithOrderInfo.class)))
			)
		}
	)
	public List<TradeWithOrderInfo> getAssetTrades(@Parameter(ref = "assetId") @PathParam("assetId") int assetId, @Parameter(ref = "otherAssetId") @PathParam("otherAssetId") int otherAssetId, 
			@Parameter(ref = "limit") @QueryParam("limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			if (!repository.getAssetRepository().assetExists(assetId))
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_ASSET_ID);

			if (!repository.getAssetRepository().assetExists(otherAssetId))
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_ASSET_ID);

			List<TradeData> trades = repository.getAssetRepository().getTrades(assetId, otherAssetId);

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, trades.size());
			int toIndex = limit == 0 ? trades.size() : Integer.min(fromIndex + limit, trades.size());
			trades = trades.subList(fromIndex, toIndex);

			// Expanding remaining entries
			List<TradeWithOrderInfo> fullTrades = new ArrayList<>();
			for (TradeData trade : trades)
				fullTrades.add(new TradeWithOrderInfo(repository, trade));

			return fullTrades;
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/issue")
	@Operation(
		summary = "Issue new asset",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = IssueAssetRequest.class)
			)
		)
	)
	public String issueAsset(IssueAssetRequest issueAssetRequest) {
		// required: issuer (pubkey), name, description, quantity, isDivisible, fee
		// optional: reference
		// returns: raw tx
		return "";
	}

}
