package org.qora.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;

@Path("/peers")
@Produces({
	MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN
})
@Tag(
	name = "Peers"
)
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
							implementation = PeerData.class
						)
					)
				)
			)
		}
	)
	public List<PeerData> getSelfPeers() {
		return Network.getInstance().getSelfPeers();
	}

	@POST
	@Operation(
		summary = "Add new peer address",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
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
	public String addPeer(String peerAddress) {
		Security.checkApiCallAllowed(request);

		try (final Repository repository = RepositoryManager.getRepository()) {
			String[] peerParts = peerAddress.split(":");

			// Expecting one or two parts
			if (peerParts.length < 1 || peerParts.length > 2)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

			String hostname = peerParts[0];
			int port = peerParts.length == 2 ? Integer.parseInt(peerParts[1]) : Settings.DEFAULT_LISTEN_PORT;

			InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);

			PeerData peerData = new PeerData(socketAddress);
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
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
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
	public String removePeer(String peerAddress) {
		Security.checkApiCallAllowed(request);

		try (final Repository repository = RepositoryManager.getRepository()) {
			String[] peerParts = peerAddress.split(":");

			// Expecting one or two parts
			if (peerParts.length < 1 || peerParts.length > 2)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

			String hostname = peerParts[0];
			int port = peerParts.length == 2 ? Integer.parseInt(peerParts[1]) : Settings.DEFAULT_LISTEN_PORT;

			InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);

			PeerData peerData = new PeerData(socketAddress);

			int numDeleted = repository.getNetworkRepository().delete(peerData);
			repository.saveChanges();

			return numDeleted != 0 ? "true" : "false";
		} catch (IllegalArgumentException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

}
