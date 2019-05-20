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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.qora.api.ApiError;
import org.qora.api.ApiErrors;
import org.qora.api.ApiException;
import org.qora.api.ApiExceptionFactory;
import org.qora.api.model.BlockForgerSummary;
import org.qora.block.Block;
import org.qora.crypto.Crypto;
import org.qora.data.account.AccountData;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.EnableForgingTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ValidationResult;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.EnableForgingTransactionTransformer;
import org.qora.utils.Base58;

@Path("/blocks")
@Tag(name = "Blocks")
public class BlocksResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/signature/{signature}")
	@Operation(
		summary = "Fetch block using base58 signature",
		description = "Returns the block that matches the given signature",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getBlock(@PathParam("signature") String signature58) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().fromSignature(signature);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/signature/{signature}/transactions")
	@Operation(
		summary = "Fetch block using base58 signature",
		description = "Returns the block that matches the given signature",
		responses = {
			@ApiResponse(
				description = "the block",
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
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public List<TransactionData> getBlockTransactions(@PathParam("signature") String signature58, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			if (repository.getBlockRepository().getHeightFromSignature(signature) == 0)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_NO_EXISTS);

			return repository.getBlockRepository().getTransactionsFromSignature(signature, limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/first")
	@Operation(
		summary = "Fetch genesis block",
		description = "Returns the genesis block",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getFirstBlock() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().fromHeight(1);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/last")
	@Operation(
		summary = "Fetch last/newest block in blockchain",
		description = "Returns the last valid block",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getLastBlock() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().getLastBlock();
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/child/{signature}")
	@Operation(
		summary = "Fetch child block using base58 signature of parent block",
		description = "Returns the child block of the block that matches the given signature",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getChild(@PathParam("signature") String signature58) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);

			// Check block exists
			if (blockData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_NO_EXISTS);

			BlockData childBlockData = repository.getBlockRepository().fromReference(signature);

			// Check child block exists
			if (childBlockData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_NO_EXISTS);

			return childBlockData;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/generatingbalance")
	@Operation(
		summary = "Generating balance of next block",
		description = "Calculates the generating balance of the block that will follow the last block",
		responses = {
			@ApiResponse(
				description = "the generating balance",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						implementation = BigDecimal.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public BigDecimal getGeneratingBalance() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().getLastBlock();
			Block block = new Block(repository, blockData);
			return block.calcNextBlockGeneratingBalance();
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/generatingbalance/{signature}")
	@Operation(
		summary = "Generating balance of block after specific block",
		description = "Calculates the generating balance of the block that will follow the block that matches the signature",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						implementation = BigDecimal.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public BigDecimal getGeneratingBalance(@PathParam("signature") String signature58) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);

			// Check block exists
			if (blockData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_NO_EXISTS);

			Block block = new Block(repository, blockData);
			return block.calcNextBlockGeneratingBalance();
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/time")
	@Operation(
		summary = "Estimated time to forge next block",
		description = "Calculates the time it should take for the network to generate the next block",
		responses = {
			@ApiResponse(
				description = "the time in seconds",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "number"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public long getTimePerBlock() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().getLastBlock();
			return Block.calcForgingDelay(blockData.getGeneratingBalance());
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/time/{generatingbalance}")
	@Operation(
		summary = "Estimated time to forge block given generating balance",
		description = "Calculates the time it should take for the network to generate blocks based on specified generating balance",
		responses = {
			@ApiResponse(
				description = "the time", // in seconds?
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "number"
					)
				)
			)
		}
	)
	public long getTimePerBlock(@PathParam("generatingbalance") BigDecimal generatingbalance) {
		return Block.calcForgingDelay(generatingbalance);
	}

	@GET
	@Path("/height")
	@Operation(
		summary = "Current blockchain height",
		description = "Returns the block height of the last block.",
		responses = {
			@ApiResponse(
				description = "the height",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "number"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public int getHeight() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().getBlockchainHeight();
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/height/{signature}")
	@Operation(
		summary = "Height of specific block",
		description = "Returns the block height of the block that matches the given signature",
		responses = {
			@ApiResponse(
				description = "the height",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "number"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public int getHeight(@PathParam("signature") String signature58) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);

			// Check block exists
			if (blockData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_NO_EXISTS);

			return blockData.getHeight();
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/byheight/{height}")
	@Operation(
		summary = "Fetch block using block height",
		description = "Returns the block with given height",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getByHeight(@PathParam("height") int height) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromHeight(height);
			if (blockData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_NO_EXISTS);

			return blockData;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/range/{height}")
	@Operation(
		summary = "Fetch blocks starting with given height",
		description = "Returns blocks starting with given height.",
		responses = {
			@ApiResponse(
				description = "blocks",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = BlockData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE
	})
	public List<BlockData> getBlockRange(@PathParam("height") int height, @Parameter(
		ref = "count"
	) @QueryParam("count") int count) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<BlockData> blocks = new ArrayList<>();

			for (/* count already set */; count > 0; --count, ++height) {
				BlockData blockData = repository.getBlockRepository().fromHeight(height);
				if (blockData == null)
					// Run out of blocks!
					break;

				blocks.add(blockData);
			}

			return blocks;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/forger/{address}")
	@Operation(
		summary = "Fetch blocks forged by address",
		responses = {
			@ApiResponse(
				description = "blocks",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = BlockData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.PUBLIC_KEY_NOT_FOUND, ApiError.REPOSITORY_ISSUE})
	public List<BlockData> getBlocksByForger(@PathParam("address") String address, @Parameter(
			ref = "limit"
			) @QueryParam("limit") Integer limit, @Parameter(
				ref = "offset"
			) @QueryParam("offset") Integer offset, @Parameter(
				ref = "reverse"
			) @QueryParam("reverse") Boolean reverse) {
		if (!Crypto.isValidAddress(address))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Get public key from address
			AccountData accountData = repository.getAccountRepository().getAccount(address);
			if (accountData == null || accountData.getPublicKey() == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.PUBLIC_KEY_NOT_FOUND);

			return repository.getBlockRepository().getBlocksWithGenerator(accountData.getPublicKey(), limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/forgers")
	@Operation(
		summary = "Show summary of block forgers",
		responses = {
			@ApiResponse(
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = BlockForgerSummary.class
						)
					)
				)
			)
		}
	)
	public List<BlockForgerSummary> getBlockForgers(@Parameter(
			ref = "limit"
			) @QueryParam("limit") Integer limit, @Parameter(
				ref = "offset"
			) @QueryParam("offset") Integer offset, @Parameter(
				ref = "reverse"
			) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().getBlockForgers(limit, offset, reverse);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/enableforging")
	@Operation(
		summary = "Build raw, unsigned, ENABLE_FORGING transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = EnableForgingTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, ENABLE_FORGING transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION, ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String enableForging(EnableForgingTransactionData transactionData) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = EnableForgingTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

}
