package org.qora.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
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

import org.qora.account.Account;
import org.qora.api.ApiError;
import org.qora.api.ApiErrors;
import org.qora.api.ApiException;
import org.qora.api.ApiExceptionFactory;
import org.qora.api.model.TradeWithOrderInfo;
import org.qora.api.resource.TransactionsResource.ConfirmationStatus;
import org.qora.crypto.Crypto;
import org.qora.data.account.AccountBalanceData;
import org.qora.data.account.AccountData;
import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.data.asset.TradeData;
import org.qora.data.transaction.CancelAssetOrderTransactionData;
import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ValidationResult;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.CancelAssetOrderTransactionTransformer;
import org.qora.transform.transaction.CreateAssetOrderTransactionTransformer;
import org.qora.transform.transaction.IssueAssetTransactionTransformer;
import org.qora.utils.Base58;

@Path("/assets")
@Produces({
	MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN
})
@Tag(
	name = "Assets"
)
public class AssetsResource {

	@Context
	HttpServletRequest request;

	@GET
	@Operation(
		summary = "List all known assets",
		responses = {
			@ApiResponse(
				description = "asset info",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = AssetData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public List<AssetData> getAllAssets(@Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getAssetRepository().getAllAssets(limit, offset, reverse);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
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
				content = @Content(
					schema = @Schema(
						implementation = AssetData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_CRITERIA, ApiError.INVALID_ASSET_ID, ApiError.REPOSITORY_ISSUE
	})
	public AssetData getAssetInfo(@QueryParam("assetId") Integer assetId, @QueryParam("assetName") String assetName) {
		if (assetId == null && (assetName == null || assetName.isEmpty()))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		try (final Repository repository = RepositoryManager.getRepository()) {
			AssetData assetData = null;

			if (assetId != null)
				assetData = repository.getAssetRepository().fromAssetId(assetId);
			else
				assetData = repository.getAssetRepository().fromAssetName(assetName);

			if (assetData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ASSET_ID);

			return assetData;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/holders/{assetid}")
	@Operation(
		summary = "List holders of an asset",
		responses = {
			@ApiResponse(
				description = "asset holders",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = AccountBalanceData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_CRITERIA, ApiError.INVALID_ASSET_ID, ApiError.REPOSITORY_ISSUE
	})
	public List<AccountBalanceData> getAssetHolders(@PathParam("assetid") int assetId, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			if (!repository.getAssetRepository().assetExists(assetId))
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ASSET_ID);

			return repository.getAccountRepository().getAssetBalances(assetId, limit, offset, reverse);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/orderbook/{assetid}/{otherassetid}")
	@Operation(
		summary = "Asset order book",
		description = "Returns open orders, offering {assetid} for {otherassetid} in return.",
		responses = {
			@ApiResponse(
				description = "asset orders",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = OrderData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_ASSET_ID, ApiError.REPOSITORY_ISSUE
	})
	public List<OrderData> getAssetOrders(@Parameter(
		ref = "assetid"
	) @PathParam("assetid") int assetId, @Parameter(
		ref = "otherassetid"
	) @PathParam("otherassetid") int otherAssetId, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			if (!repository.getAssetRepository().assetExists(assetId))
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ASSET_ID);

			if (!repository.getAssetRepository().assetExists(otherAssetId))
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ASSET_ID);

			return repository.getAssetRepository().getOpenOrders(assetId, otherAssetId, limit, offset, reverse);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/trades/{assetid}/{otherassetid}")
	@Operation(
		summary = "Asset trades",
		description = "Returns successful trades of {assetid} for {otherassetid}.<br>" + "Does NOT include trades of {otherassetid} for {assetid}!<br>"
				+ "\"Initiating\" order is the order that caused the actual trade by matching up with the \"target\" order.",
		responses = {
			@ApiResponse(
				description = "asset trades",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = TradeWithOrderInfo.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_ASSET_ID, ApiError.REPOSITORY_ISSUE
	})
	public List<TradeWithOrderInfo> getAssetTrades(@Parameter(
		ref = "assetid"
	) @PathParam("assetid") int assetId, @Parameter(
		ref = "otherassetid"
	) @PathParam("otherassetid") int otherAssetId, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			if (!repository.getAssetRepository().assetExists(assetId))
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ASSET_ID);

			if (!repository.getAssetRepository().assetExists(otherAssetId))
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ASSET_ID);

			List<TradeData> trades = repository.getAssetRepository().getTrades(assetId, otherAssetId, limit, offset, reverse);

			// Expanding remaining entries
			List<TradeWithOrderInfo> fullTrades = new ArrayList<>();
			for (TradeData tradeData : trades) {
				OrderData initiatingOrderData = repository.getAssetRepository().fromOrderId(tradeData.getInitiator());
				OrderData targetOrderData = repository.getAssetRepository().fromOrderId(tradeData.getTarget());
				fullTrades.add(new TradeWithOrderInfo(tradeData, initiatingOrderData, targetOrderData));
			}

			return fullTrades;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/order/{orderid}")
	@Operation(
		summary = "Fetch asset order",
		description = "Returns asset order info.",
		responses = {
			@ApiResponse(
				description = "asset order",
				content = @Content(
					schema = @Schema(
						implementation = OrderData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_ORDER_ID, ApiError.ORDER_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public OrderData getAssetOrder(@PathParam("orderid") String orderId58) {
		// Decode orderID
		byte[] orderId;
		try {
			orderId = Base58.decode(orderId58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ORDER_ID, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			OrderData orderData = repository.getAssetRepository().fromOrderId(orderId);
			if (orderData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.ORDER_NO_EXISTS);

			return orderData;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/order/{orderid}/trades")
	@Operation(
		summary = "Fetch asset order's matching trades",
		description = "Returns asset order trades",
		responses = {
			@ApiResponse(
				description = "asset trades",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = TradeData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_ORDER_ID, ApiError.ORDER_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public List<TradeData> getAssetOrderTrades(@PathParam("orderid") String orderId58, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		// Decode orderID
		byte[] orderId;
		try {
			orderId = Base58.decode(orderId58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ORDER_ID, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			OrderData orderData = repository.getAssetRepository().fromOrderId(orderId);
			if (orderData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.ORDER_NO_EXISTS);

			return repository.getAssetRepository().getOrdersTrades(orderId, limit, offset, reverse);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/address/{address}")
	@Operation(
		summary = "All assets owned by this address",
		description = "Returns the list of assets for this address, with balances.",
		responses = {
			@ApiResponse(
				description = "the list of assets",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = AccountBalanceData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_ADDRESS, ApiError.REPOSITORY_ISSUE
	})
	public List<AccountBalanceData> getAssets(@PathParam("address") String address, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		if (!Crypto.isValidAddress(address))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getAccountRepository().getAllBalances(address, limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/balance/{assetid}/{address}")
	@Operation(
		summary = "Asset-specific balance request",
		description = "Returns the confirmed balance of the given address for the given asset key.",
		responses = {
			@ApiResponse(
				description = "the balance",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string",
						format = "number"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_ADDRESS, ApiError.REPOSITORY_ISSUE
	})
	public BigDecimal getAssetBalance(@PathParam("assetid") long assetid, @PathParam("address") String address) {
		if (!Crypto.isValidAddress(address))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Account account = new Account(repository, address);
			return account.getConfirmedBalance(assetid);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/orders/{address}")
	@Operation(
		summary = "Asset orders created by this address",
		responses = {
			@ApiResponse(
				description = "Asset orders",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = OrderData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_ADDRESS, ApiError.ADDRESS_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public List<OrderData> getAssetOrders(@PathParam("address") String address, @QueryParam("includeClosed") boolean includeClosed,
			@QueryParam("includeFulfilled") boolean includeFulfilled, @Parameter(
				ref = "limit"
			) @QueryParam("limit") Integer limit, @Parameter(
				ref = "offset"
			) @QueryParam("offset") Integer offset, @Parameter(
				ref = "reverse"
			) @QueryParam("reverse") Boolean reverse) {
		if (!Crypto.isValidAddress(address))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			AccountData accountData = repository.getAccountRepository().getAccount(address);

			if (accountData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.ADDRESS_NO_EXISTS);

			byte[] publicKey = accountData.getPublicKey();
			if (publicKey == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.ADDRESS_NO_EXISTS);

			return repository.getAssetRepository().getAccountsOrders(publicKey, includeClosed, includeFulfilled, limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/transactions/{assetid}")
	@Operation(
		summary = "Transactions related to asset",
		responses = {
			@ApiResponse(
				description = "Asset transactions",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = TransactionData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_ADDRESS, ApiError.INVALID_ASSET_ID, ApiError.REPOSITORY_ISSUE
	})
	public List<TransactionData> getAssetTransactions(@Parameter(
		ref = "assetid"
	) @PathParam("assetid") int assetId, @Parameter(
		description = "whether to include confirmed, unconfirmed or both",
		required = true
	) @QueryParam("confirmationStatus") ConfirmationStatus confirmationStatus, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			if (!repository.getAssetRepository().assetExists(assetId))
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ASSET_ID);

			return repository.getTransactionRepository().getAssetTransactions(assetId, confirmationStatus, limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/order/delete")
	@Operation(
		summary = "Cancel existing asset order",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = CancelAssetOrderTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, CANCEL_ORDER transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.NON_PRODUCTION, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE, ApiError.TRANSACTION_INVALID
	})
	public String cancelOrder(CancelAssetOrderTransactionData transactionData) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = CancelAssetOrderTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/issue")
	@Operation(
		summary = "Issue new asset",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = IssueAssetTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, ISSUE_ASSET transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.NON_PRODUCTION, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE, ApiError.TRANSACTION_INVALID
	})
	public String issueAsset(IssueAssetTransactionData transactionData) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = IssueAssetTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/order")
	@Operation(
		summary = "Create asset order",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = CreateAssetOrderTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, CREATE_ORDER transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.NON_PRODUCTION, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE, ApiError.TRANSACTION_INVALID
	})
	public String createOrder(CreateAssetOrderTransactionData transactionData) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = CreateAssetOrderTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

}
