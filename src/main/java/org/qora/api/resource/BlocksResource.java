package org.qora.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.qora.api.ApiError;
import org.qora.api.ApiErrors;
import org.qora.api.ApiException;
import org.qora.api.ApiExceptionFactory;
import org.qora.api.model.BlockWithTransactions;
import org.qora.block.Block;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.utils.Base58;

@Path("/blocks")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
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
						implementation = BlockWithTransactions.class
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
	public BlockWithTransactions getBlock(@PathParam("signature") String signature58, @Parameter(ref = "includeTransactions") @QueryParam("includeTransactions") boolean includeTransactions) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);
			return packageBlockData(repository, blockData, includeTransactions);
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
						implementation = BlockWithTransactions.class
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
	public BlockWithTransactions getFirstBlock(@Parameter(ref = "includeTransactions") @QueryParam("includeTransactions") boolean includeTransactions) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromHeight(1);
			return packageBlockData(repository, blockData, includeTransactions);
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
						implementation = BlockWithTransactions.class
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
	public BlockWithTransactions getLastBlock(@Parameter(ref = "includeTransactions") @QueryParam("includeTransactions") boolean includeTransactions) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().getLastBlock();
			return packageBlockData(repository, blockData, includeTransactions);
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
						implementation = BlockWithTransactions.class
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
	public BlockWithTransactions getChild(@PathParam("signature") String signature58, @Parameter(ref = "includeTransactions") @QueryParam("includeTransactions") boolean includeTransactions) {
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

			// Checking child exists is handled by packageBlockData()
			return packageBlockData(repository, childBlockData, includeTransactions);
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
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
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
	@ApiErrors({ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
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
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
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
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
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
	@ApiErrors({ApiError.INVALID_SIGNATURE, ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
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
						implementation = BlockWithTransactions.class
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
	public BlockWithTransactions getByHeight(@PathParam("height") int height, @Parameter(ref = "includeTransactions") @QueryParam("includeTransactions") boolean includeTransactions) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromHeight(height);
			return packageBlockData(repository, blockData, includeTransactions);
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
					schema = @Schema(
						implementation = BlockWithTransactions.class
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.BLOCK_NO_EXISTS, ApiError.REPOSITORY_ISSUE})
	public List<BlockWithTransactions> getBlockRange(@PathParam("height") int height, @Parameter(ref = "count") @QueryParam("count") int count) {
		boolean includeTransactions = false;

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<BlockWithTransactions> blocks = new ArrayList<BlockWithTransactions>();

			for (/* count already set */; count > 0; --count, ++height) {
				BlockData blockData = repository.getBlockRepository().fromHeight(height);
				if (blockData == null)
					// Run out of blocks!
					break;

				blocks.add(packageBlockData(repository, blockData, includeTransactions));
			}

			return blocks;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	/** 
	 * Returns block, optionally including transactions.
	 * <p>
	 * Throws ApiException using ApiError.BLOCK_NO_EXISTS if blockData is null.
	 * 
	 * @param repository
	 * @param blockData
	 * @param includeTransactions
	 * @return packaged block, with optional transactions
	 * @throws DataException
	 * @throws ApiException ApiError.BLOCK_NO_EXISTS
	 */
	private BlockWithTransactions packageBlockData(Repository repository, BlockData blockData, boolean includeTransactions) throws DataException {
		if (blockData == null)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_NO_EXISTS);

		List<TransactionData> transactions = null;
		if (includeTransactions) {
			Block block = new Block(repository, blockData);
			transactions = block.getTransactions().stream().map(transaction -> transaction.getTransactionData()).collect(Collectors.toList());
		}

		return new BlockWithTransactions(blockData, transactions);
	}

}
