package org.qora.group;

import java.util.Arrays;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupInviteData;
import org.qora.data.group.GroupJoinRequestData;
import org.qora.data.group.GroupMemberData;
import org.qora.data.transaction.AddGroupAdminTransactionData;
import org.qora.data.transaction.CancelGroupInviteTransactionData;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.GroupInviteTransactionData;
import org.qora.data.transaction.GroupKickTransactionData;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.LeaveGroupTransactionData;
import org.qora.data.transaction.RemoveGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.GroupRepository;
import org.qora.repository.Repository;

public class Group {

	// Properties
	private Repository repository;
	private GroupData groupData;

	// Useful constants
	public static final int MAX_NAME_SIZE = 400;
	public static final int MAX_DESCRIPTION_SIZE = 4000;
	/** Max size of kick/ban reason */
	public static final int MAX_REASON_SIZE = 400;

	// Constructors

	/**
	 * Construct Group business object using info from create group transaction.
	 * 
	 * @param repository
	 * @param createGroupTransactionData
	 */
	public Group(Repository repository, CreateGroupTransactionData createGroupTransactionData) {
		this.repository = repository;
		this.groupData = new GroupData(createGroupTransactionData.getOwner(), createGroupTransactionData.getGroupName(),
				createGroupTransactionData.getDescription(), createGroupTransactionData.getTimestamp(), createGroupTransactionData.getIsOpen(),
				createGroupTransactionData.getSignature());
	}

	/**
	 * Construct Group business object using existing group in repository.
	 * 
	 * @param repository
	 * @param groupName
	 * @throws DataException
	 */
	public Group(Repository repository, String groupName) throws DataException {
		this.repository = repository;
		this.groupData = this.repository.getGroupRepository().fromGroupName(groupName);
	}

	// Processing

	public void create(CreateGroupTransactionData createGroupTransactionData) throws DataException {
		this.repository.getGroupRepository().save(this.groupData);

		// Add owner as admin too
		this.repository.getGroupRepository()
				.save(new GroupAdminData(this.groupData.getGroupName(), this.groupData.getOwner(), createGroupTransactionData.getSignature()));

		// Add owner as member too
		this.repository.getGroupRepository().save(new GroupMemberData(this.groupData.getGroupName(), this.groupData.getOwner(), this.groupData.getCreated(),
				createGroupTransactionData.getSignature()));
	}

	public void uncreate() throws DataException {
		// Repository takes care of cleaning up ancilliary data!
		this.repository.getGroupRepository().delete(this.groupData.getGroupName());
	}

	private void revert() throws DataException {
		TransactionData previousTransactionData = this.repository.getTransactionRepository().fromSignature(this.groupData.getReference());
		if (previousTransactionData == null)
			throw new DataException("Unable to revert group transaction as referenced transaction not found in repository");

		switch (previousTransactionData.getType()) {
			case CREATE_GROUP:
				CreateGroupTransactionData previousCreateGroupTransactionData = (CreateGroupTransactionData) previousTransactionData;
				this.groupData.setOwner(previousCreateGroupTransactionData.getOwner());
				this.groupData.setDescription(previousCreateGroupTransactionData.getDescription());
				this.groupData.setIsOpen(previousCreateGroupTransactionData.getIsOpen());
				this.groupData.setUpdated(null);
				break;

			case UPDATE_GROUP:
				UpdateGroupTransactionData previousUpdateGroupTransactionData = (UpdateGroupTransactionData) previousTransactionData;
				this.groupData.setOwner(previousUpdateGroupTransactionData.getNewOwner());
				this.groupData.setDescription(previousUpdateGroupTransactionData.getNewDescription());
				this.groupData.setIsOpen(previousUpdateGroupTransactionData.getNewIsOpen());
				this.groupData.setUpdated(previousUpdateGroupTransactionData.getTimestamp());
				break;

			default:
				throw new IllegalStateException("Unable to revert group transaction due to unsupported referenced transaction");
		}

		// Previous owner will still be admin and member at this point
	}

	public void update(UpdateGroupTransactionData updateGroupTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = updateGroupTransactionData.getGroupName();

		// Update reference in transaction data
		updateGroupTransactionData.setGroupReference(this.groupData.getReference());

		// New group reference is this transaction's signature
		this.groupData.setReference(updateGroupTransactionData.getSignature());

		// Update Group's owner and description
		this.groupData.setOwner(updateGroupTransactionData.getNewOwner());
		this.groupData.setDescription(updateGroupTransactionData.getNewDescription());
		this.groupData.setIsOpen(updateGroupTransactionData.getNewIsOpen());
		this.groupData.setUpdated(updateGroupTransactionData.getTimestamp());

		// Save updated group data
		groupRepository.save(this.groupData);

		String newOwner = updateGroupTransactionData.getNewOwner();

		// New owner should be a member if not already
		if (!groupRepository.memberExists(groupName, newOwner)) {
			GroupMemberData groupMemberData = new GroupMemberData(groupName, newOwner, updateGroupTransactionData.getTimestamp(),
					updateGroupTransactionData.getSignature());
			groupRepository.save(groupMemberData);
		}

		// New owner should be an admin if not already
		if (!groupRepository.adminExists(groupName, newOwner)) {
			GroupAdminData groupAdminData = new GroupAdminData(groupName, newOwner, updateGroupTransactionData.getSignature());
			groupRepository.save(groupAdminData);
		}

		// Previous owner retained as admin and member
	}

	public void revert(UpdateGroupTransactionData updateGroupTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = updateGroupTransactionData.getGroupName();

		// Previous group reference is taken from this transaction's cached copy
		this.groupData.setReference(updateGroupTransactionData.getGroupReference());

		// Previous Group's owner and/or description taken from referenced transaction
		this.revert();

		// Save reverted group data
		groupRepository.save(this.groupData);

		// If ownership changed we need to do more work. Note groupData's owner is reverted at this point.
		String newOwner = updateGroupTransactionData.getNewOwner();
		if (!this.groupData.getOwner().equals(newOwner)) {
			// If this update caused [what was] new owner to become admin, then revoke that now.
			// (It's possible they were an admin prior to being given ownership so we need to retain that).
			GroupAdminData groupAdminData = groupRepository.getAdmin(groupName, newOwner);
			if (Arrays.equals(groupAdminData.getGroupReference(), updateGroupTransactionData.getSignature()))
				groupRepository.deleteAdmin(groupName, newOwner);

			// If this update caused [what was] new owner to become member, then revoke that now.
			// (It's possible they were a member prior to being given ownership so we need to retain that).
			GroupMemberData groupMemberData = groupRepository.getMember(groupName, newOwner);
			if (Arrays.equals(groupMemberData.getGroupReference(), updateGroupTransactionData.getSignature()))
				groupRepository.deleteMember(groupName, newOwner);
		}
	}

	public void promoteToAdmin(AddGroupAdminTransactionData addGroupAdminTransactionData) throws DataException {
		GroupAdminData groupAdminData = new GroupAdminData(addGroupAdminTransactionData.getGroupName(), addGroupAdminTransactionData.getMember(),
				addGroupAdminTransactionData.getSignature());
		this.repository.getGroupRepository().save(groupAdminData);
	}

	public void unpromoteToAdmin(AddGroupAdminTransactionData addGroupAdminTransactionData) throws DataException {
		this.repository.getGroupRepository().deleteAdmin(addGroupAdminTransactionData.getGroupName(), addGroupAdminTransactionData.getMember());
	}

	public void demoteFromAdmin(RemoveGroupAdminTransactionData removeGroupAdminTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = removeGroupAdminTransactionData.getGroupName();
		String admin = removeGroupAdminTransactionData.getAdmin();

		// Save admin's promotion transaction reference for orphaning purposes
		GroupAdminData groupAdminData = groupRepository.getAdmin(groupName, admin);
		removeGroupAdminTransactionData.setGroupReference(groupAdminData.getGroupReference());

		// Demote
		groupRepository.deleteAdmin(groupName, admin);
	}

	public void undemoteFromAdmin(RemoveGroupAdminTransactionData removeGroupAdminTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = removeGroupAdminTransactionData.getGroupName();
		String admin = removeGroupAdminTransactionData.getAdmin();

		// Rebuild admin entry using stored promotion transaction reference
		GroupAdminData groupAdminData = new GroupAdminData(groupName, admin, removeGroupAdminTransactionData.getGroupReference());
		groupRepository.save(groupAdminData);
	}

	public void kick(GroupKickTransactionData groupKickTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = groupKickTransactionData.getGroupName();
		String member = groupKickTransactionData.getMember();

		// Store membership and (optionally) adminship transactions for orphaning purposes
		GroupAdminData groupAdminData = groupRepository.getAdmin(groupName, member);
		if (groupAdminData != null) {
			groupKickTransactionData.setAdminReference(groupAdminData.getGroupReference());

			groupRepository.deleteAdmin(groupName, member);
		}

		GroupMemberData groupMemberData = groupRepository.getMember(groupName, member);
		groupKickTransactionData.setMemberReference(groupMemberData.getGroupReference());

		groupRepository.deleteMember(groupName, member);
	}

	public void unkick(GroupKickTransactionData groupKickTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = groupKickTransactionData.getGroupName();
		String member = groupKickTransactionData.getMember();

		// Rebuild member entry using stored transaction reference
		TransactionData membershipTransactionData = this.repository.getTransactionRepository().fromSignature(groupKickTransactionData.getMemberReference());
		GroupMemberData groupMemberData = new GroupMemberData(groupName, member, membershipTransactionData.getTimestamp(),
				membershipTransactionData.getSignature());
		groupRepository.save(groupMemberData);

		if (groupKickTransactionData.getAdminReference() != null) {
			// Rebuild admin entry using stored transaction reference
			GroupAdminData groupAdminData = new GroupAdminData(groupName, member, groupKickTransactionData.getAdminReference());
			groupRepository.save(groupAdminData);
		}
	}

	public void invite(GroupInviteTransactionData groupInviteTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = groupInviteTransactionData.getGroupName();
		Account inviter = new PublicKeyAccount(this.repository, groupInviteTransactionData.getAdminPublicKey());

		// If there is a pending "join request" then add new group member
		if (groupRepository.joinRequestExists(groupName, groupInviteTransactionData.getInvitee())) {
			GroupMemberData groupMemberData = new GroupMemberData(groupName, groupInviteTransactionData.getInvitee(), groupInviteTransactionData.getTimestamp(),
					groupInviteTransactionData.getSignature());
			groupRepository.save(groupMemberData);

			// Delete join request
			groupRepository.deleteJoinRequest(groupName, groupInviteTransactionData.getInvitee());

			return;
		}

		Long expiry;
		int timeToLive = groupInviteTransactionData.getTimeToLive();
		if (timeToLive == 0)
			expiry = null;
		else
			expiry = groupInviteTransactionData.getTimestamp() + timeToLive;

		GroupInviteData groupInviteData = new GroupInviteData(groupName, inviter.getAddress(), groupInviteTransactionData.getInvitee(), expiry,
				groupInviteTransactionData.getSignature());
		groupRepository.save(groupInviteData);
	}

	public void uninvite(GroupInviteTransactionData groupInviteTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = groupInviteTransactionData.getGroupName();
		Account inviter = new PublicKeyAccount(this.repository, groupInviteTransactionData.getAdminPublicKey());
		String invitee = groupInviteTransactionData.getInvitee();

		// Put back any "join request"
		if (groupRepository.memberExists(groupName, invitee)) {
			GroupJoinRequestData groupJoinRequestData = new GroupJoinRequestData(groupName, invitee);
			groupRepository.save(groupJoinRequestData);

			// Delete member
			groupRepository.deleteMember(groupName, invitee);
		}

		// Delete invite
		groupRepository.deleteInvite(groupName, inviter.getAddress(), invitee);
	}

	public void cancelInvite(CancelGroupInviteTransactionData cancelGroupInviteTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = cancelGroupInviteTransactionData.getGroupName();
		Account inviter = new PublicKeyAccount(this.repository, cancelGroupInviteTransactionData.getAdminPublicKey());
		String invitee = cancelGroupInviteTransactionData.getInvitee();

		// Save invite's transaction signature for orphaning purposes
		GroupInviteData groupInviteData = groupRepository.getInvite(groupName, inviter.getAddress(), invitee);
		cancelGroupInviteTransactionData.setGroupReference(groupInviteData.getReference());

		// Delete invite
		groupRepository.deleteInvite(groupName, inviter.getAddress(), invitee);
	}

	public void uncancelInvite(CancelGroupInviteTransactionData cancelGroupInviteTransactionData) throws DataException {
		// Reinstate invite
		TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(cancelGroupInviteTransactionData.getGroupReference());
		invite((GroupInviteTransactionData) transactionData);

		cancelGroupInviteTransactionData.setGroupReference(null);
	}

	public void join(JoinGroupTransactionData joinGroupTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = joinGroupTransactionData.getGroupName();
		Account joiner = new PublicKeyAccount(this.repository, joinGroupTransactionData.getJoinerPublicKey());

		// Set invite transactions' group-reference to this transaction's signature so the invites can be put back if we orphan this join
		// Delete any pending invites
		List<GroupInviteData> invites = groupRepository.getInvitesByInvitee(groupName, joiner.getAddress());
		for (GroupInviteData invite : invites) {
			TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(invite.getReference());
			((GroupInviteTransactionData) transactionData).setGroupReference(joinGroupTransactionData.getSignature());

			groupRepository.deleteInvite(groupName, invite.getInviter(), joiner.getAddress());
		}

		// If there were no invites and this group is "closed" (i.e. invite-only) then
		// this is now a pending "join request"
		if (invites.isEmpty() && !groupData.getIsOpen()) {
			GroupJoinRequestData groupJoinRequestData = new GroupJoinRequestData(groupName, joiner.getAddress());
			groupRepository.save(groupJoinRequestData);
			return;
		}

		// Actually add new member to group
		GroupMemberData groupMemberData = new GroupMemberData(groupName, joiner.getAddress(), joinGroupTransactionData.getTimestamp(),
				joinGroupTransactionData.getSignature());
		groupRepository.save(groupMemberData);

	}

	public void unjoin(JoinGroupTransactionData joinGroupTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = joinGroupTransactionData.getGroupName();
		Account joiner = new PublicKeyAccount(this.repository, joinGroupTransactionData.getJoinerPublicKey());

		groupRepository.deleteMember(groupName, joiner.getAddress());

		// Put back any pending invites
		List<GroupInviteTransactionData> inviteTransactions = this.repository.getTransactionRepository()
				.getInvitesWithGroupReference(joinGroupTransactionData.getSignature());
		for (GroupInviteTransactionData inviteTransaction : inviteTransactions) {
			this.invite(inviteTransaction);

			// Remove group-reference
			inviteTransaction.setGroupReference(null);
			this.repository.getTransactionRepository().save(inviteTransaction);
		}
	}

	public void leave(LeaveGroupTransactionData leaveGroupTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = leaveGroupTransactionData.getGroupName();
		Account leaver = new PublicKeyAccount(this.repository, leaveGroupTransactionData.getLeaverPublicKey());

		// Potentially record reference to transaction that restores previous admin state.
		// Owners can't leave as that would leave group ownerless and in unrecoverable state.

		// Owners are also admins, so skip if owner
		if (!leaver.getAddress().equals(this.groupData.getOwner())) {
			// Fetch admin data for leaver
			GroupAdminData groupAdminData = groupRepository.getAdmin(groupName, leaver.getAddress());

			if (groupAdminData != null) {
				// Leaver is admin - use promotion transaction reference as restore-state reference
				leaveGroupTransactionData.setAdminReference(groupAdminData.getGroupReference());

				// Remove as admin
				groupRepository.deleteAdmin(groupName, leaver.getAddress());
			}
		}

		// Save membership transaction reference
		GroupMemberData groupMemberData = groupRepository.getMember(groupName, leaver.getAddress());
		leaveGroupTransactionData.setMemberReference(groupMemberData.getGroupReference());

		// Remove as member
		groupRepository.deleteMember(leaveGroupTransactionData.getGroupName(), leaver.getAddress());
	}

	public void unleave(LeaveGroupTransactionData leaveGroupTransactionData) throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = leaveGroupTransactionData.getGroupName();
		Account leaver = new PublicKeyAccount(this.repository, leaveGroupTransactionData.getLeaverPublicKey());

		// Rejoin as member
		TransactionData membershipTransactionData = this.repository.getTransactionRepository().fromSignature(leaveGroupTransactionData.getMemberReference());
		groupRepository
				.save(new GroupMemberData(groupName, leaver.getAddress(), membershipTransactionData.getTimestamp(), membershipTransactionData.getSignature()));

		// Put back any admin state based on referenced group-related transaction
		byte[] adminTransactionSignature = leaveGroupTransactionData.getAdminReference();
		if (adminTransactionSignature != null) {
			GroupAdminData groupAdminData = new GroupAdminData(leaveGroupTransactionData.getGroupName(), leaver.getAddress(), adminTransactionSignature);
			groupRepository.save(groupAdminData);
		}
	}

}
