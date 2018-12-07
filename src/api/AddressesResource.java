package api;

import globalization.Translator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import data.account.AccountBalanceData;
import qora.account.Account;
import qora.assets.Asset;
import qora.crypto.Crypto;
import repository.Repository;
import repository.RepositoryManager;
import utils.Base58;

@Path("addresses")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Extension(name = "translation", properties = {
		@ExtensionProperty(name="path", value="/Api/AddressesResource")
	}
)
@Tag(name = "addresses")
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
		summary = "Fetch reference for next transaction to be created by address",
		description = "Returns the 64-byte long base58-encoded signature of last transaction created by address, failing that: the first incoming transaction to address. Returns \"false\" if there is no transactions.",
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
		if (!Crypto.isValidAddress(address))
			throw this.apiErrorFactory.createError(ApiError.INVALID_ADDRESS);

		byte[] lastReference = null;
		try (final Repository repository = RepositoryManager.getRepository()) {
			Account account = new Account(repository, address);
			lastReference = account.getLastReference();
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
		summary = "Fetch reference for next transaction to be created by address, considering unconfirmed transactions",
		description = "Returns the 64-byte long base58-encoded signature of last transaction, including unconfirmed, created by address, failing that: the first incoming transaction. Returns \\\"false\\\" if there is no transactions.",
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
		if (!Crypto.isValidAddress(address))
			throw this.apiErrorFactory.createError(ApiError.INVALID_ADDRESS);

		byte[] lastReference = null;
		try (final Repository repository = RepositoryManager.getRepository()) {
			Account account = new Account(repository, address);
			lastReference = account.getUnconfirmedLastReference();
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
	@Path("/validate/{address}")
	@Operation(
		summary = "Validates the given address",
		description = "Returns true/false.",
		extensions = {
			@Extension(name = "translation", properties = {
				@ExtensionProperty(name="path", value="GET validate:address"),
				@ExtensionProperty(name="summary.key", value="operation:summary"),
				@ExtensionProperty(name="description.key", value="operation:description"),
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
	@Path("/balance/{address}")
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
				content = @Content(schema = @Schema(name = "balance", type = "number")),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public BigDecimal getGeneratingBalance(@PathParam("address") String address) {
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
	@Path("/assetbalance/{assetid}/{address}")
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
	@Path("/assets/{address}")
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
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = AccountBalanceData.class))),
				extensions = {
					@Extension(name = "translation", properties = {
						@ExtensionProperty(name="description.key", value="success_response:description")
					})
				}
			)
		}
	)
	public List<AccountBalanceData> getAssets(@PathParam("address") String address) {
		if (!Crypto.isValidAddress(address))
			throw this.apiErrorFactory.createError(ApiError.INVALID_ADDRESS);

        try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getAccountRepository().getAllBalances(address);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
            throw this.apiErrorFactory.createError(ApiError.UNKNOWN, e);
        }
	}

	@GET
	@Path("/balance/{address}/{confirmations}")
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
		throw new UnsupportedOperationException();
	}
	
}
