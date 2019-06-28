package api;

import globalization.Translator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import qora.crypto.Crypto;

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
import repository.TransactionRepository;
import utils.Base58;

@Path("transactions")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Extension(name = "translation", properties = {
		@ExtensionProperty(name="path", value="/Api/TransactionsResource")
	}
)
@Tag(name = "transactions")
public class TransactionsResource {

	@Context
	HttpServletRequest request;

	private ApiErrorFactory apiErrorFactory;

	public TransactionsResource() {
		this(new ApiErrorFactory(Translator.getInstance()));
	}

	public TransactionsResource(ApiErrorFactory apiErrorFactory) {
		this.apiErrorFactory = apiErrorFactory;
	}

	@GET
	@Path("/address/{address}")
	@Operation(
		summary = "Fetch transactions involving address",
		description = "Returns list of transactions",
		parameters = {
				@Parameter(in = ParameterIn.PATH, name = "address", description = "Account's address", schema = @Schema(type = "string"))
		},
		extensions = {
			@Extension(name = "translation", properties = {
					@ExtensionProperty(name="path", value="GET block:signature"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
					@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\"]", parseValue = true),
				})
		},
		responses = {
			@ApiResponse(
				description = "list of transactions",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = TransactionData.class))),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public List<TransactionData> getAddressTransactions(@PathParam("address") String address, @Parameter(ref = "limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		if (!Crypto.isValidAddress(address))
			throw this.apiErrorFactory.createError(ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionRepository txRepo = repository.getTransactionRepository();

			List<byte[]> signatures = txRepo.getAllSignaturesInvolvingAddress(address);

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, signatures.size());
			int toIndex = limit == 0 ? signatures.size() : Integer.min(fromIndex + limit, signatures.size());
			signatures = signatures.subList(fromIndex, toIndex);

			// Expand signatures to transactions
			List<TransactionData> transactions = new ArrayList<TransactionData>(signatures.size());
			for (byte[] signature : signatures)
				transactions.add(txRepo.fromSignature(signature));

			return transactions;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw this.apiErrorFactory.createError(ApiError.REPOSITORY_ISSUE, e);
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
	public List<TransactionData> getBlockTransactions(@PathParam("signature") String signature, @Parameter(ref = "limit") @QueryParam("limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		// decode signature
		byte[] signatureBytes;
		try {
			signatureBytes = Base58.decode(signature);
		} catch (NumberFormatException e) {
			throw this.apiErrorFactory.createError(ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<TransactionData> transactions = repository.getBlockRepository().getTransactionsFromSignature(signatureBytes);

			// check if block exists
			if(transactions == null)
				throw this.apiErrorFactory.createError(ApiError.BLOCK_NO_EXISTS);

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, transactions.size());
			int toIndex = limit == 0 ? transactions.size() : Integer.min(fromIndex + limit, transactions.size());
			transactions = transactions.subList(fromIndex, toIndex);

			return transactions;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw this.apiErrorFactory.createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

}
