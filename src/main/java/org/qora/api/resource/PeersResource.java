package org.qora.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.qora.api.ApiError;
import org.qora.api.ApiErrors;
import org.qora.api.ApiException;
import org.qora.api.ApiExceptionFactory;
import org.qora.api.Security;
import org.qora.api.model.ConnectedPeer;
import org.qora.data.network.PeerData;
import org.qora.network.Network;
import org.qora.network.PeerAddress;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;

@Path("/peers")
@Tag(name = "Peers")
public class PeersResource {

	@Context
	HttpServletRequest request;

	@GET
	@Operation(
		summary = "Fetch list of connected peers",
		responses = {
			@ApiResponse(
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(
						schema = @Schema(
							implementation = ConnectedPeer.class
						)
					)
				)
			)
		}
	)
	public List<ConnectedPeer> getPeers() {
		return Network.getInstance().getConnectedPeers().stream().map(peer -> new ConnectedPeer(peer)).collect(Collectors.toList());
	}

	@GET
	@Path("/known")
	@Operation(
		summary = "Fetch list of all known peers",
		responses = {
			@ApiResponse(
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(
						schema = @Schema(
							implementation = PeerData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public List<PeerData> getKnownPeers() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getNetworkRepository().getAllPeers();
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/self")
	@Operation(
		summary = "Fetch list of peers that connect to self",
		responses = {
			@ApiResponse(
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(
						schema = @Schema(
							implementation = PeerAddress.class
						)
					)
				)
			)
		}
	)
	public List<PeerAddress> getSelfPeers() {
		return Network.getInstance().getSelfPeers();
	}

	@POST
	@Operation(
		summary = "Add new peer address",
		description = "Specify a new peer using hostname, IPv4 address, IPv6 address and optional port number preceeded with colon (e.g. :9889)<br>"
				+ "Note that IPv6 literal addresses must be surrounded with brackets.<br>" + "Examples:<br><ul>" + "<li>some-peer.example.com</li>"
				+ "<li>some-peer.example.com:9889</li>" + "<li>10.1.2.3</li>" + "<li>10.1.2.3:9889</li>" + "<li>[2001:d8b::1]</li>"
				+ "<li>[2001:d8b::1]:9889</li>" + "</ul>",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string",
					example = "some-peer.example.com"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "true if accepted",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_DATA, ApiError.REPOSITORY_ISSUE
	})
	public String addPeer(String address) {
		Security.checkApiCallAllowed(request);

		try (final Repository repository = RepositoryManager.getRepository()) {
			PeerAddress peerAddress = PeerAddress.fromString(address);

			PeerData peerData = new PeerData(peerAddress, System.currentTimeMillis(), "API");
			repository.getNetworkRepository().save(peerData);
			repository.saveChanges();

			return "true";
		} catch (IllegalArgumentException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@DELETE
	@Operation(
		summary = "Remove peer address from database",
		description = "Specify peer to be removed using hostname, IPv4 address, IPv6 address and optional port number preceeded with colon (e.g. :9889)<br>"
				+ "Note that IPv6 literal addresses must be surrounded with brackets.<br>" + "Examples:<br><ul>" + "<li>some-peer.example.com</li>"
				+ "<li>some-peer.example.com:9889</li>" + "<li>10.1.2.3</li>" + "<li>10.1.2.3:9889</li>" + "<li>[2001:d8b::1]</li>"
				+ "<li>[2001:d8b::1]:9889</li>" + "</ul>",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string",
					example = "some-peer.example.com"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "true if removed, false if not found",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_DATA, ApiError.REPOSITORY_ISSUE
	})
	public String removePeer(String address) {
		Security.checkApiCallAllowed(request);

		try {
			PeerAddress peerAddress = PeerAddress.fromString(address);

			boolean wasKnown = Network.getInstance().forgetPeer(peerAddress);
			return wasKnown ? "true" : "false";
		} catch (IllegalArgumentException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@DELETE
	@Path("/known")
	@Operation(
		summary = "Remove all known peers from database",
		responses = {
			@ApiResponse(
				description = "true if any peers were removed, false if there were no peers to delete",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_DATA, ApiError.REPOSITORY_ISSUE
	})
	public String removeKnownPeers(String address) {
		Security.checkApiCallAllowed(request);

		try {
			int numDeleted = Network.getInstance().forgetAllPeers();

			return numDeleted != 0 ? "true" : "false";
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

}
