package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import qora.transaction.Transaction.TransactionType;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import data.transaction.GenesisTransactionData;
import data.transaction.PaymentTransactionData;
import data.transaction.TransactionData;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import utils.Base58;

@Path("transactions")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Extension(name = "translation", properties = {
		@ExtensionProperty(name="path", value="/Api/TransactionsResource")
	}
)
@Tag(name = "Transactions")
public class TransactionsResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/signature/{signature}")
	@Operation(
		summary = "Fetch transaction using transaction signature",
		description = "Returns transaction",
		extensions = {
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_SIGNATURE\", \"TRANSACTION_NO_EXISTS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "a transaction",
				content = @Content(schema = @Schema(implementation = TransactionData.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public TransactionData getTransactions(@PathParam("signature") String signature58) {
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			if (transactionData == null)
				throw ApiErrorFactory.getInstance().createError(ApiError.TRANSACTION_NO_EXISTS);

			return transactionData;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/block/{signature}")
	@Operation(
		summary = "Fetch transactions using block signature",
		description = "Returns list of transactions",
		extensions = {
			@Extension(name = "translation", properties = {
					@ExtensionProperty(name="path", value="GET block:signature"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
					@ExtensionProperty(name="apiErrors", value="[\"INVALID_SIGNATURE\", \"BLOCK_NO_EXISTS\"]", parseValue = true),
				})
		},
		responses = {
			@ApiResponse(
				description = "list of transactions",
				content = @Content(array = @ArraySchema(schema = @Schema(
							oneOf = { GenesisTransactionData.class, PaymentTransactionData.class }
						))),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public List<TransactionData> getBlockTransactions(@PathParam("signature") String signature58, @Parameter(ref = "limit") @QueryParam("limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<TransactionData> transactions = repository.getBlockRepository().getTransactionsFromSignature(signature);

			// check if block exists
			if(transactions == null)
				throw ApiErrorFactory.getInstance().createError(ApiError.BLOCK_NO_EXISTS);

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, transactions.size());
			int toIndex = limit == 0 ? transactions.size() : Integer.min(fromIndex + limit, transactions.size());
			transactions = transactions.subList(fromIndex, toIndex);

			return transactions;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/unconfirmed")
	@Operation(
		summary = "List unconfirmed transactions",
		description = "Returns transactions",
		responses = {
			@ApiResponse(
				description = "transactions",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = TransactionData.class))),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public List<TransactionData> getUnconfirmedTransactions() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getTransactionRepository().getAllUnconfirmedTransactions();
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/search")
	@Operation(
		summary = "Find matching transactions",
		description = "Returns transactions that match criteria. At least either txType or address must be provided.",
		/*
		parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "txType", description = "Transaction type", schema = @Schema(type = "integer")),
			@Parameter(in = ParameterIn.QUERY, name = "address", description = "Account's address", schema = @Schema(type = "string")),
			@Parameter(in = ParameterIn.QUERY, name = "startBlock", description = "Start block height", schema = @Schema(type = "integer")),
			@Parameter(in = ParameterIn.QUERY, name = "blockLimit", description = "Maximum number of blocks to search", schema = @Schema(type = "integer"))
		},
		*/
		responses = {
			@ApiResponse(
				description = "transactions",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = TransactionData.class))),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public List<TransactionData> searchTransactions(@QueryParam("startBlock") Integer startBlock, @QueryParam("blockLimit") Integer blockLimit,
			@QueryParam("txType") Integer txTypeNum, @QueryParam("address") String address, @Parameter(ref = "limit") @QueryParam("limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		if ((txTypeNum == null || txTypeNum == 0) && (address == null || address.isEmpty()))
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_CRITERIA);

		TransactionType txType = null;
		if (txTypeNum != null) {
			txType = TransactionType.valueOf(txTypeNum);
			if (txType == null)
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_CRITERIA);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<byte[]> signatures  = repository.getTransactionRepository().getAllSignaturesMatchingCriteria(startBlock, blockLimit, txType, address);

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, signatures.size());
			int toIndex = limit == 0 ? signatures.size() : Integer.min(fromIndex + limit, signatures.size());
			signatures = signatures.subList(fromIndex, toIndex);

			// Expand signatures to transactions
			List<TransactionData> transactions = new ArrayList<TransactionData>(signatures.size());
			for (byte[] signature : signatures)
				transactions.add(repository.getTransactionRepository().fromSignature(signature));

			return transactions;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

}
