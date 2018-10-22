package api;

import data.account.AccountData;
import data.block.BlockData;
import globalization.Translator;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.math.BigDecimal;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import qora.account.Account;
import qora.assets.Asset;
import qora.crypto.Crypto;
import repository.Repository;
import repository.RepositoryManager;
import utils.Base58;

@Path("addresses")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@OpenAPIDefinition(
	extensions = @Extension(name = "translation", properties = {
		@ExtensionProperty(name="path", value="/Api/AddressesResource")
	})
)
public class AddressesResource {

	@Context
	HttpServletRequest request;
	
	private ApiErrorFactory apiErrorFactory;

	public AddressesResource() {
		this(new ApiErrorFactory(Translator.getInstance()));
	}

	public AddressesResource(ApiErrorFactory apiErrorFactory) {
		this.apiErrorFactory = apiErrorFactory;
	}

	@GET
	@Path("/lastreference/{address}")
	@Operation(
		description = "Returns the 64-byte long base58-encoded signature of last transaction where the address is delivered as creator. Or the first incoming transaction. Returns \"false\" if there is no transactions.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET lastreference:address"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the base58-encoded transaction signature or \"false\"",
				content = @Content(schema = @Schema(implementation = String.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public String getLastReference(
		@Parameter(description = "a base58-encoded address", required = true) @PathParam("address") String address
	) {
		Security.checkApiCallAllowed("GET addresses/lastreference", request);

		if (!Crypto.isValidAddress(address))
			throw this.apiErrorFactory.createError(ApiError.INVALID_ADDRESS);

		byte[] lastReference = null;
        try (final Repository repository = RepositoryManager.getRepository()) {
			Account account = new Account(repository, address);
			account.getLastReference();
			
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
		
		if(lastReference == null || lastReference.length == 0) {
			return "false"; 
		} else {
			return Base58.encode(lastReference);
		}
	}
	
	@GET
	@Path("/lastreference/{address}/unconfirmed")
	@Operation(
		description = "Returns the 64-byte long base58-encoded signature of last transaction including unconfirmed where the address is delivered as creator. Or the first incoming transaction. Returns \\\"false\\\" if there is no transactions.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET lastreference:address:unconfirmed"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the base58-encoded transaction signature",
				content = @Content(schema = @Schema(implementation = String.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public String getLastReferenceUnconfirmed(@PathParam("address") String address) {
		Security.checkApiCallAllowed("GET addresses/lastreference", request);

		// XXX: is this method needed?
		
		throw new UnsupportedOperationException();
	}

	@GET
	@Path("/validate/{address}")
	@Operation(
		description = "Validates the given address. Returns true/false.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET validate:address"),
				@ExtensionProperty(name="description.key", value="operation:description")
			})
		},
		responses = {
			@ApiResponse(
				//description = "",
				content = @Content(schema = @Schema(implementation = Boolean.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public boolean validate(@PathParam("address") String address) {
		Security.checkApiCallAllowed("GET addresses/validate", request);

		return Crypto.isValidAddress(address);
	}
	
	@GET
	@Path("/generatingbalance/{address}")
	@Operation(
		description = "Return the generating balance of the given address.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET generatingbalance:address"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\"]", parseValue = true),
			})
		},
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
	public BigDecimal getGeneratingBalanceOfAddress(@PathParam("address") String address) {
		Security.checkApiCallAllowed("GET addresses/generatingbalance", request);

		if (!Crypto.isValidAddress(address))
			throw this.apiErrorFactory.createError(ApiError.INVALID_ADDRESS);

        try (final Repository repository = RepositoryManager.getRepository()) {
			Account account = new Account(repository, address);
			return account.getGeneratingBalance();
			
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("balance/{address}")
	@Operation(
		description = "Returns the confirmed balance of the given address.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET balance:address"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the balance",
				content = @Content(schema = @Schema(implementation = BigDecimal.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BigDecimal getGeneratingBalance(@PathParam("address") String address) {
		Security.checkApiCallAllowed("GET addresses/balance", request);
		
		if (!Crypto.isValidAddress(address))
			throw this.apiErrorFactory.createError(ApiError.INVALID_ADDRESS);

        try (final Repository repository = RepositoryManager.getRepository()) {
			Account account = new Account(repository, address);
			return account.getConfirmedBalance(Asset.QORA);
			
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("assetbalance/{assetid}/{address}")
	@Operation(
		description = "Returns the confirmed balance of the given address for the given asset key.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET assetbalance:assetid:address"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\", \"INVALID_ASSET_ID\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the balance",
				content = @Content(schema = @Schema(implementation = BigDecimal.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BigDecimal getAssetBalance(@PathParam("assetid") long assetid, @PathParam("address") String address) {
		Security.checkApiCallAllowed("GET addresses/assetbalance", request);
		
		if (!Crypto.isValidAddress(address))
			throw this.apiErrorFactory.createError(ApiError.INVALID_ADDRESS);

        try (final Repository repository = RepositoryManager.getRepository()) {
			Account account = new Account(repository, address);
			return account.getConfirmedBalance(assetid);
			
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("assets/{address}")
	@Operation(
		description = "Returns the list of assets for this address with balances.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET assets:address"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the list of assets",
				content = @Content(schema = @Schema(implementation = String.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public String getAssetBalance(@PathParam("address") String address) {
		Security.checkApiCallAllowed("GET addresses/assets", request);
		
		throw new UnsupportedOperationException();
	}

	@GET
	@Path("balance/{address}/{confirmations}")
	@Operation(
		description = "Calculates the balance of the given address after the given confirmations.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET balance:address:confirmations"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the balance",
				content = @Content(schema = @Schema(implementation = String.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public String getGeneratingBalance(@PathParam("address") String address, @PathParam("confirmations") int confirmations) {
		Security.checkApiCallAllowed("GET addresses/balance", request);
		
		throw new UnsupportedOperationException();
	}

	@GET
	@Path("/publickey/{address}")
	@Operation(
		description = "Returns the 32-byte long base58-encoded account publickey of the given address.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET publickey:address"),
				@ExtensionProperty(name="description.key", value="operation:description")
			}),
			@Extension(properties = {
				@ExtensionProperty(name="apiErrors", value="[\"INVALID_ADDRESS\"]", parseValue = true),
			})
		},
		responses = {
			@ApiResponse(
				description = "the publickey",
				content = @Content(schema = @Schema(implementation = String.class)),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public String getPublicKey(@PathParam("address") String address) {
		Security.checkApiCallAllowed("GET addresses/publickey", request);
		
		throw new UnsupportedOperationException();
	}
	
}
