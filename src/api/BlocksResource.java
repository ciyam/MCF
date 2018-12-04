package api;

import data.block.BlockData;
import globalization.Translator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import javax.servlet.http.HttpServletRequest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import qora.block.Block;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import utils.Base58;

@Path("blocks")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Extension(name = "translation", properties = {
		@ExtensionProperty(name="path", value="/Api/BlocksResource")
	}
)
@Tag(name = "blocks")
public class BlocksResource {

	@Context
	HttpServletRequest request;

	private ApiErrorFactory apiErrorFactory;

	public BlocksResource() {
		this(new ApiErrorFactory(Translator.getInstance()));
	}

	public BlocksResource(ApiErrorFactory apiErrorFactory) {
		this.apiErrorFactory = apiErrorFactory;
	}

	@GET
	@Path("/{signature}")
	@Operation(
		summary = "Fetch block using base58 signature",
		description = "returns the block that matches the given signature",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET signature"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_SIGNATURE\", \"BLOCK_NO_EXISTS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(schema = @Schema(implementation = BlockData.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BlockData getBlock(@PathParam("signature") String signature) {
		Security.checkApiCallAllowed("GET blocks", request);

		// decode signature
		byte[] signatureBytes;
		try
		{
			signatureBytes = Base58.decode(signature);
		}
		catch(Exception e)
		{
            throw this.apiErrorFactory.createError(ApiError.INVALID_SIGNATURE, e);
		}

        try (final Repository repository = RepositoryManager.getRepository()) {
            BlockData blockData = repository.getBlockRepository().fromSignature(signatureBytes);
				
			// check if block exists
			if(blockData == null)
				throw this.apiErrorFactory.createError(ApiError.BLOCK_NO_EXISTS);

			return blockData;

		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/first")
	@Operation(
		summary = "Fetch genesis block",
		description = "returns the genesis block",
		extensions = @Extension(name = "translation", properties = {
			@ExtensionProperty(name="path", value="GET first"),
			@ExtensionProperty(name="description.key", value="operation:description")
		}),
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(schema = @Schema(implementation = BlockData.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BlockData getFirstBlock() {
		Security.checkApiCallAllowed("GET blocks/first", request);

        try (final Repository repository = RepositoryManager.getRepository()) {
            BlockData blockData = repository.getBlockRepository().fromHeight(1);
			return blockData;

		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/last")
	@Operation(
		summary = "Fetch last/newest block in blockchain",
		description = "returns the last valid block",
		extensions = @Extension(name = "translation", properties = {
			@ExtensionProperty(name="path", value="GET last"),
			@ExtensionProperty(name="description.key", value="operation:description")
		}),
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(schema = @Schema(implementation = BlockData.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BlockData getLastBlock() {
		Security.checkApiCallAllowed("GET blocks/last", request);

        try (final Repository repository = RepositoryManager.getRepository()) {
            BlockData blockData = repository.getBlockRepository().getLastBlock();
			return blockData;

		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/child/{signature}")
	@Operation(
		summary = "Fetch child block using base58 signature of parent block",
		description = "returns the child block of the block that matches the given signature",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET child:signature"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_SIGNATURE\", \"BLOCK_NO_EXISTS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(schema = @Schema(implementation = BlockData.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BlockData getChild(@PathParam("signature") String signature) {
		Security.checkApiCallAllowed("GET blocks/child", request);

		// decode signature
		byte[] signatureBytes;
		try {
			signatureBytes = Base58.decode(signature);
		} catch (NumberFormatException e) {
			throw this.apiErrorFactory.createError(ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromSignature(signatureBytes);

			// check if block exists
			if(blockData == null)
				throw this.apiErrorFactory.createError(ApiError.BLOCK_NO_EXISTS);

			BlockData childBlockData = repository.getBlockRepository().fromReference(signatureBytes);

			// check if child exists
			if(childBlockData == null)
				throw this.apiErrorFactory.createError(ApiError.BLOCK_NO_EXISTS);

			return childBlockData;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw this.apiErrorFactory.createError(ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/generatingbalance")
	@Operation(
		description = "calculates the generating balance of the block that will follow the last block",
		extensions = @Extension(name = "translation", properties = {
			@ExtensionProperty(name="path", value="GET generatingbalance"),
			@ExtensionProperty(name="description.key", value="operation:description")
		}),
		responses = {
			@ApiResponse(
				description = "the generating balance",
				content = @Content(schema = @Schema(implementation = BigDecimal.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BigDecimal getGeneratingBalance() {
		Security.checkApiCallAllowed("GET blocks/generatingbalance", request);

        try (final Repository repository = RepositoryManager.getRepository()) {
            BlockData blockData = repository.getBlockRepository().getLastBlock();
			Block block = new Block(repository, blockData);
			return block.calcNextBlockGeneratingBalance();
			
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/generatingbalance/{signature}")
	@Operation(
		description = "calculates the generating balance of the block that will follow the block that matches the signature",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET generatingbalance:signature"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_SIGNATURE\", \"BLOCK_NO_EXISTS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(schema = @Schema(implementation = BigDecimal.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BigDecimal getGeneratingBalance(@PathParam("signature") String signature) {
		Security.checkApiCallAllowed("GET blocks/generatingbalance", request);

		// decode signature
		byte[] signatureBytes;
		try
		{
			signatureBytes = Base58.decode(signature);
		}
		catch(Exception e)
		{
            throw this.apiErrorFactory.createError(ApiError.INVALID_SIGNATURE, e);
		}

        try (final Repository repository = RepositoryManager.getRepository()) {
            BlockData blockData = repository.getBlockRepository().fromSignature(signatureBytes);
				
			// check if block exists
			if(blockData == null)
				throw this.apiErrorFactory.createError(ApiError.BLOCK_NO_EXISTS);

			Block block = new Block(repository, blockData);
			return block.calcNextBlockGeneratingBalance();
			
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/time")
	@Operation(
		description = "calculates the time it should take for the network to generate the next block",
		extensions = @Extension(name = "translation", properties = {
			@ExtensionProperty(name="path", value="GET time"),
			@ExtensionProperty(name="description.key", value="operation:description")
		}),
		responses = {
			@ApiResponse(
				description = "the time in seconds", // in seconds?
				content = @Content(schema = @Schema(implementation = long.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public long getTimePerBlock() {
		Security.checkApiCallAllowed("GET blocks/time", request);

        try (final Repository repository = RepositoryManager.getRepository()) {
            BlockData blockData = repository.getBlockRepository().getLastBlock();
			return Block.calcForgingDelay(blockData.getGeneratingBalance());

		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/time/{generatingbalance}")
	@Operation(
		description = "calculates the time it should take for the network to generate blocks when the current generating balance in the network is the specified generating balance",
		extensions = @Extension(name = "translation", properties = {
			@ExtensionProperty(name="path", value="GET time:generatingbalance"),
			@ExtensionProperty(name="description.key", value="operation:description")
		}),
		responses = {
			@ApiResponse(
				description = "the time", // in seconds?
				content = @Content(schema = @Schema(implementation = long.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public long getTimePerBlock(@PathParam("generating") BigDecimal generatingbalance) {
		Security.checkApiCallAllowed("GET blocks/time", request);

		return Block.calcForgingDelay(generatingbalance);
	}

	@GET
	@Path("/height")
	@Operation(
		description = "returns the block height of the last block.",
		extensions = @Extension(name = "translation", properties = {
			@ExtensionProperty(name="path", value="GET height"),
			@ExtensionProperty(name="description.key", value="operation:description")
		}),
		responses = {
			@ApiResponse(
				description = "the height",
				content = @Content(schema = @Schema(implementation = int.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public int getHeight() {
		Security.checkApiCallAllowed("GET blocks/height", request);

        try (final Repository repository = RepositoryManager.getRepository()) {
            return repository.getBlockRepository().getBlockchainHeight();

		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/height/{signature}")
	@Operation(
		description = "returns the block height of the block that matches the given signature",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET height:signature"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_SIGNATURE\", \"BLOCK_NO_EXISTS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the height",
				content = @Content(schema = @Schema(implementation = int.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public int getHeight(@PathParam("signature") String signature) {
		Security.checkApiCallAllowed("GET blocks/height", request);

		// decode signature
		byte[] signatureBytes;
		try
		{
			signatureBytes = Base58.decode(signature);
		}
		catch(Exception e)
		{
            throw this.apiErrorFactory.createError(ApiError.INVALID_SIGNATURE, e);
		}

        try (final Repository repository = RepositoryManager.getRepository()) {
            BlockData blockData = repository.getBlockRepository().fromSignature(signatureBytes);
				
			// check if block exists
			if(blockData == null)
				throw this.apiErrorFactory.createError(ApiError.BLOCK_NO_EXISTS);

			return blockData.getHeight();
			
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/byheight/{height}")
	@Operation(
		description = "returns the block whith given height",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET byheight:height"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"BLOCK_NO_EXISTS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(schema = @Schema(implementation = BlockData.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BlockData getbyHeight(@PathParam("height") int height) {
		Security.checkApiCallAllowed("GET blocks/byheight", request);

        try (final Repository repository = RepositoryManager.getRepository()) {
            BlockData blockData = repository.getBlockRepository().fromHeight(height);
				
			// check if block exists
			if(blockData == null)
				throw this.apiErrorFactory.createError(ApiError.BLOCK_NO_EXISTS);

			return blockData;
			
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}
}
