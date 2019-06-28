package org.qora.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.qora.api.ApiError;
import org.qora.api.ApiErrors;
import org.qora.api.ApiExceptionFactory;
import org.qora.api.model.GroupWithMemberInfo;
import org.qora.api.model.GroupWithMemberInfo.MemberInfo;
import org.qora.crypto.Crypto;
import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupBanData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupInviteData;
import org.qora.data.group.GroupJoinRequestData;
import org.qora.data.group.GroupMemberData;
import org.qora.data.transaction.AddGroupAdminTransactionData;
import org.qora.data.transaction.CancelGroupInviteTransactionData;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.GroupBanTransactionData;
import org.qora.data.transaction.GroupInviteTransactionData;
import org.qora.data.transaction.GroupKickTransactionData;
import org.qora.data.transaction.CancelGroupBanTransactionData;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.LeaveGroupTransactionData;
import org.qora.data.transaction.RemoveGroupAdminTransactionData;
import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ValidationResult;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.AddGroupAdminTransactionTransformer;
import org.qora.transform.transaction.CancelGroupInviteTransactionTransformer;
import org.qora.transform.transaction.CreateGroupTransactionTransformer;
import org.qora.transform.transaction.GroupBanTransactionTransformer;
import org.qora.transform.transaction.GroupInviteTransactionTransformer;
import org.qora.transform.transaction.GroupKickTransactionTransformer;
import org.qora.transform.transaction.CancelGroupBanTransactionTransformer;
import org.qora.transform.transaction.JoinGroupTransactionTransformer;
import org.qora.transform.transaction.LeaveGroupTransactionTransformer;
import org.qora.transform.transaction.RemoveGroupAdminTransactionTransformer;
import org.qora.transform.transaction.UpdateGroupTransactionTransformer;
import org.qora.utils.Base58;

@Path("/groups")
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
@Tag(name = "Groups")
public class GroupsResource {

	@Context
	HttpServletRequest request;

	@GET
	@Operation(
		summary = "List all groups",
		responses = {
			@ApiResponse(
				description = "group info",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(schema = @Schema(implementation = GroupData.class))
				)
			)
		}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<GroupData> getAllGroups(@Parameter(ref = "limit") @QueryParam("limit") int limit, @Parameter(ref = "offset") @QueryParam("offset") int offset) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<GroupData> groups = repository.getGroupRepository().getAllGroups();

			// Pagination would take effect here (or as part of the repository access)
			int fromIndex = Integer.min(offset, groups.size());
			int toIndex = limit == 0 ? groups.size() : Integer.min(fromIndex + limit, groups.size());
			groups = groups.subList(fromIndex, toIndex);

			return groups;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/owner/{address}")
	@Operation(
		summary = "List all groups owned by address",
		responses = {
			@ApiResponse(
				description = "group info",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(schema = @Schema(implementation = GroupData.class))
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.REPOSITORY_ISSUE})
	public List<GroupData> getGroupsByOwner(@PathParam("address") String owner) {
		if (!Crypto.isValidAddress(owner))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<GroupData> groups = repository.getGroupRepository().getGroupsByOwner(owner);

			return groups;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/member/{address}")
	@Operation(
		summary = "List all groups where address is a member",
		responses = {
			@ApiResponse(
				description = "group info",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(schema = @Schema(implementation = GroupData.class))
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.REPOSITORY_ISSUE})
	public List<GroupData> getGroupsWithMember(@PathParam("address") String member) {
		if (!Crypto.isValidAddress(member))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<GroupData> groups = repository.getGroupRepository().getGroupsWithMember(member);

			return groups;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/{groupname}")
	@Operation(
		summary = "Info on group",
		responses = {
			@ApiResponse(
				description = "group info",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					schema = @Schema(implementation = GroupWithMemberInfo.class)
				)
			)
		}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public GroupWithMemberInfo getGroup(@PathParam("groupname") String groupName, @QueryParam("includeMembers") boolean includeMembers) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			GroupData groupData = repository.getGroupRepository().fromGroupName(groupName);
			if (groupData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.GROUP_UNKNOWN);

			List<MemberInfo> members = null;
			Integer memberCount = null;

			if (includeMembers) {
				List<GroupMemberData> groupMembers = repository.getGroupRepository().getGroupMembers(groupData.getGroupId());

				// Convert to MemberInfo
				members = groupMembers.stream().map(groupMemberData -> new MemberInfo(groupMemberData)).collect(Collectors.toList());

				memberCount = members.size();
			} else {
				// Just count members instead
				memberCount = repository.getGroupRepository().countGroupMembers(groupData.getGroupId());
			}

			// Always include admins
			List<GroupAdminData> groupAdmins = repository.getGroupRepository().getGroupAdmins(groupData.getGroupId());

			// We only need admin addresses
			List<String> adminAddresses = groupAdmins.stream().map(groupAdminData -> groupAdminData.getAdmin()).collect(Collectors.toList());

			return new GroupWithMemberInfo(groupData, adminAddresses, members, memberCount);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}


	@POST
	@Path("/create")
	@Operation(
		summary = "Build raw, unsigned, CREATE_GROUP transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = CreateGroupTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, CREATE_GROUP transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String createGroup(CreateGroupTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = CreateGroupTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/update")
	@Operation(
		summary = "Build raw, unsigned, UPDATE_GROUP transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = UpdateGroupTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, UPDATE_GROUP transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String updateGroup(UpdateGroupTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = UpdateGroupTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/addadmin")
	@Operation(
		summary = "Build raw, unsigned, ADD_GROUP_ADMIN transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = AddGroupAdminTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, ADD_GROUP_ADMIN transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String addGroupAdmin(AddGroupAdminTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = AddGroupAdminTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/removeadmin")
	@Operation(
		summary = "Build raw, unsigned, REMOVE_GROUP_ADMIN transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = RemoveGroupAdminTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, REMOVE_GROUP_ADMIN transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String removeGroupAdmin(RemoveGroupAdminTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = RemoveGroupAdminTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/ban")
	@Operation(
		summary = "Build raw, unsigned, GROUP_BAN transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = GroupBanTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, GROUP_BAN transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String groupBan(GroupBanTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = GroupBanTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/ban/cancel")
	@Operation(
		summary = "Build raw, unsigned, CANCEL_GROUP_BAN transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = CancelGroupBanTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, CANCEL_GROUP_BAN transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String cancelGroupBan(CancelGroupBanTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = CancelGroupBanTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/kick")
	@Operation(
		summary = "Build raw, unsigned, GROUP_KICK transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = GroupKickTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, GROUP_KICK transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String groupKick(GroupKickTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = GroupKickTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/invite")
	@Operation(
		summary = "Build raw, unsigned, GROUP_INVITE transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = GroupInviteTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, GROUP_INVITE transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String groupInvite(GroupInviteTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = GroupInviteTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/invite/cancel")
	@Operation(
		summary = "Build raw, unsigned, CANCEL_GROUP_INVITE transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = CancelGroupInviteTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, CANCEL_GROUP_INVITE transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String cancelGroupInvite(CancelGroupInviteTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = CancelGroupInviteTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/join")
	@Operation(
		summary = "Build raw, unsigned, JOIN_GROUP transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = JoinGroupTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, JOIN_GROUP transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String joinGroup(JoinGroupTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = JoinGroupTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/leave")
	@Operation(
		summary = "Build raw, unsigned, LEAVE_GROUP transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = LeaveGroupTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, LEAVE_GROUP transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String leaveGroup(LeaveGroupTransactionData transactionData) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = LeaveGroupTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/invites/{address}")
	@Operation(
		summary = "Pending group invites",
		responses = {
			@ApiResponse(
				description = "group invite",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(schema = @Schema(implementation = GroupInviteData.class))
				)
			)
		}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<GroupInviteData> getInvitesByInvitee(@PathParam("address") String invitee) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getGroupRepository().getInvitesByInvitee(invitee);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/invites/group/{groupid}")
	@Operation(
		summary = "Pending group invites",
		responses = {
			@ApiResponse(
				description = "group invite",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(schema = @Schema(implementation = GroupInviteData.class))
				)
			)
		}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<GroupInviteData> getInvitesByGroupId(@PathParam("groupid") int groupId) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getGroupRepository().getInvitesByGroupId(groupId);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/joinrequests/{groupid}")
	@Operation(
		summary = "Pending group join requests",
		responses = {
			@ApiResponse(
				description = "group join requests",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(schema = @Schema(implementation = GroupJoinRequestData.class))
				)
			)
		}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<GroupJoinRequestData> getJoinRequests(@PathParam("groupid") int groupId) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getGroupRepository().getGroupJoinRequests(groupId);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/bans/{groupid}")
	@Operation(
		summary = "Current group join bans",
		responses = {
			@ApiResponse(
				description = "group bans",
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(schema = @Schema(implementation = GroupJoinRequestData.class))
				)
			)
		}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<GroupBanData> getBans(@PathParam("groupid") int groupId) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getGroupRepository().getGroupBans(groupId);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

}