package api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import qora.account.PrivateKeyAccount;
import qora.transaction.Transaction;
import qora.transaction.Transaction.TransactionType;
import qora.transaction.Transaction.ValidationResult;

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

import com.google.common.primitives.Bytes;

import api.ApiError;
import api.ApiErrors;
import api.ApiException;
import api.ApiExceptionFactory;
import api.models.SimpleTransactionSignRequest;
import data.transaction.GenesisTransactionData;
import data.transaction.PaymentTransactionData;
import data.transaction.TransactionData;
import globalization.Translator;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import transform.TransformationException;
import transform.transaction.TransactionTransformer;
import utils.Base58;

@Path("/transactions")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Tag(name = "Transactions")
public class TransactionsResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/signature/{signature}")
	@Operation(
		summary = "Fetch transaction using transaction signature",
		description = "Returns transaction",
		responses = {
			@ApiResponse(
				description = "a transaction",
				content = @Content(
					schema = @Schema(
						implementation = TransactionData.class
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_SIGNATURE, ApiError.TRANSACTION_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
	public TransactionData getTransactions(@PathParam("signature") String signature58) {
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			if (transactionData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSACTION_NO_EXISTS);

			return transactionData;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/block/{signature}")
	@Operation(
		summary = "Fetch transactions using block signature",
		description = "Returns list of transactions",
		responses = {
			@ApiResponse(
				description = "list of transactions",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							oneOf = {
								GenesisTransactionData.class, PaymentTransactionData.class
							}
						)
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
	public List<TransactionData> getBlockTransactions(@PathParam("signature") String signature58, @Parameter(ref = "limit") @QueryParam("limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<TransactionData> transactions = repository.getBlockRepository().getTransactionsFromSignature(signature);

			// check if block exists
			if (transactions == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_NO_EXISTS);

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, transactions.size());
			int toIndex = limit == 0 ? transactions.size() : Integer.min(fromIndex + limit, transactions.size());
			transactions = transactions.subList(fromIndex, toIndex);

			return transactions;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
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
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<TransactionData> getUnconfirmedTransactions() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getTransactionRepository().getAllUnconfirmedTransactions();
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/search")
	@Operation(
		summary = "Find matching transactions",
		description = "Returns transactions that match criteria. At least either txType or address must be provided.",
		/*
		 * parameters = {
		 * 
		 * @Parameter(in = ParameterIn.QUERY, name = "txType", description = "Transaction type", schema = @Schema(type = "integer")),
		 * 
		 * @Parameter(in = ParameterIn.QUERY, name = "address", description = "Account's address", schema = @Schema(type = "string")),
		 * 
		 * @Parameter(in = ParameterIn.QUERY, name = "startBlock", description = "Start block height", schema = @Schema(type = "integer")),
		 * 
		 * @Parameter(in = ParameterIn.QUERY, name = "blockLimit", description = "Maximum number of blocks to search", schema = @Schema(type = "integer"))
		 * },
		 */
		responses = {
			@ApiResponse(
				description = "transactions",
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
	@ApiErrors({ApiError.INVALID_CRITERIA, ApiError.REPOSITORY_ISSUE})
	public List<TransactionData> searchTransactions(@QueryParam("startBlock") Integer startBlock, @QueryParam("blockLimit") Integer blockLimit, 
			@QueryParam("txType") Integer txTypeNum, @QueryParam("address") String address, @Parameter(
				ref = "limit"
			) @QueryParam("limit") int limit, @Parameter(
				ref = "offset"
			) @QueryParam("offset") int offset) {
		if ((txTypeNum == null || txTypeNum == 0) && (address == null || address.isEmpty()))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		TransactionType txType = null;
		if (txTypeNum != null) {
			txType = TransactionType.valueOf(txTypeNum);
			if (txType == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<byte[]> signatures = repository.getTransactionRepository().getAllSignaturesMatchingCriteria(startBlock, blockLimit, txType, address);

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
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/sign")
	@Operation(
		summary = "Sign a raw, unsigned transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = SimpleTransactionSignRequest.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, signed transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_PRIVATE_KEY, ApiError.TRANSFORMATION_ERROR})
	public String signTransaction(SimpleTransactionSignRequest signRequest) {
		if (signRequest.transactionBytes.length == 0)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.JSON);

		try {
			// Append null signature on the end before transformation
			byte[] rawBytes = Bytes.concat(signRequest.transactionBytes, new byte[TransactionTransformer.SIGNATURE_LENGTH]);

			TransactionData transactionData = TransactionTransformer.fromBytes(rawBytes);

			PrivateKeyAccount signer = new PrivateKeyAccount(null, signRequest.privateKey);

			Transaction transaction = Transaction.fromData(null, transactionData);
			transaction.sign(signer);

			byte[] signedBytes = TransactionTransformer.toBytes(transactionData);

			return Base58.encode(signedBytes);
		} catch (IllegalArgumentException e) {
			// Invalid private key
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PRIVATE_KEY);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		}
	}

	@POST
	@Path("/process")
	@Operation(
		summary = "Submit raw, signed transaction for processing and adding to blockchain",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string",
					description = "raw, signed transaction in base58 encoding",
					example = "base58"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "true if accepted, false otherwise",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_SIGNATURE, ApiError.INVALID_DATA, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String processTransaction(String rawBytes58) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			byte[] rawBytes = Base58.decode(rawBytes58);
			TransactionData transactionData = TransactionTransformer.fromBytes(rawBytes);

			Transaction transaction = Transaction.fromData(repository, transactionData);
			if (!transaction.isSignatureValid())
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw createTransactionInvalidException(request, result);

			repository.getTransactionRepository().save(transactionData);
			repository.getTransactionRepository().unconfirmTransaction(transactionData);
			repository.saveChanges();

			return "true";
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA, e);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/decode")
	@Operation(
		summary = "Decode a raw, signed transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string",
					description = "raw, unsigned/signed transaction in base58 encoding",
					example = "base58"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "a transaction",
				content = @Content(
					schema = @Schema(
						implementation = TransactionData.class
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_SIGNATURE, ApiError.INVALID_DATA, ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public TransactionData decodeTransaction(String rawBytes58) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			byte[] rawBytes = Base58.decode(rawBytes58);
			boolean hasSignature = true;

			TransactionData transactionData;
			try {
				transactionData = TransactionTransformer.fromBytes(rawBytes);
			} catch (TransformationException e) {
				// Maybe we're missing a signature, so append one and try one more time
				rawBytes = Bytes.concat(rawBytes, new byte[TransactionTransformer.SIGNATURE_LENGTH]);
				hasSignature = false;
				transactionData = TransactionTransformer.fromBytes(rawBytes);
			}

			Transaction transaction = Transaction.fromData(repository, transactionData);
			if (hasSignature && !transaction.isSignatureValid())
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE);

			ValidationResult result = transaction.isValid();
			if (result != ValidationResult.OK)
				throw createTransactionInvalidException(request, result);

			if (!hasSignature)
				transactionData.setSignature(null);

			return transactionData;
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA, e);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	public static ApiException createTransactionInvalidException(HttpServletRequest request, ValidationResult result) {
		String translatedResult = Translator.INSTANCE.translate("TransactionValidity", request.getLocale().getLanguage(), result.name());
		return ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSACTION_INVALID, null, translatedResult);
	}

}
