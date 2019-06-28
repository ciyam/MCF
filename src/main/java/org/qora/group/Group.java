package org.qora.group;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Map;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
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
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.GroupRepository;
import org.qora.repository.Repository;

public class Group {

	/** Group-admin quora threshold for approving transactions */
	public enum ApprovalThreshold {
		// NOTE: value needs to fit into byte
		NONE(0, false),
		ONE(1, false),
		PCT20(20, true),
		PCT40(40, true),
		PCT60(60, true),
		PCT80(80, true),
		PCT100(100, true);

		public final int value;
		public final boolean isPercentage;

		private final static Map<Integer, ApprovalThreshold> map = stream(ApprovalThreshold.values())
				.collect(toMap(threshold -> threshold.value, threshold -> threshold));

		ApprovalThreshold(int value, boolean isPercentage) {
			this.value = value;
			this.isPercentage = isPercentage;
		}

		public static ApprovalThreshold valueOf(int value) {
			return map.get(value);
		}

		private boolean meetsTheshold(int currentApprovals, int totalAdmins) {
			if (!this.isPercentage)
				return currentApprovals >= this.value;

			return currentApprovals >= (totalAdmins * 100 / this.value);
		}

		/**
		 * Returns whether transaction meets approval threshold.
		 * 
		 * @param repository
		 * @param txGroupId
		 *            transaction's groupID
		 * @param signature
		 *            transaction's signature
		 * @return true if approval still needed, false if transaction can be included in block
		 * @throws DataException
		 */
		public boolean meetsApprovalThreshold(Repository repository, int txGroupId, byte[] signature) throws DataException {
			// Fetch total number of admins in group
			final int totalAdmins = repository.getGroupRepository().countGroupAdmins(txGroupId);

			// Fetch total number of approvals for signature
			// NOT simply number of GROUP_APPROVE transactions as some may be rejecting transaction, or changed opinions
			final int currentApprovals = repository.getTransactionRepository().countTransactionApprovals(txGroupId, signature);

			return meetsTheshold(currentApprovals, totalAdmins);
		}
	}

	// Properties
	private Repository repository;
	private GroupRepository groupRepository;
	private GroupData groupData;

	// Useful constants
	public static final int NO_GROUP = 0;

	public static final int MAX_NAME_SIZE = 32;
	public static final int MAX_DESCRIPTION_SIZE = 128;
	/** Max size of kick/ban reason */
	public static final int MAX_REASON_SIZE = 128;

	// Constructors

	/**
	 * Construct Group business object using info from create group transaction.
	 * 
	 * @param repository
	 * @param createGroupTransactionData
	 */
	public Group(Repository repository, CreateGroupTransactionData createGroupTransactionData) {
		this.repository = repository;
		this.groupRepository = repository.getGroupRepository();

		this.groupData = new GroupData(createGroupTransactionData.getOwner(), createGroupTransactionData.getGroupName(),
				createGroupTransactionData.getDescription(), createGroupTransactionData.getTimestamp(), createGroupTransactionData.getIsOpen(),
				createGroupTransactionData.getApprovalThreshold(), createGroupTransactionData.getMinimumBlockDelay(),
				createGroupTransactionData.getMaximumBlockDelay(), createGroupTransactionData.getSignature(), createGroupTransactionData.getTxGroupId());
	}

	/**
	 * Construct Group business object using existing group in repository.
	 * 
	 * @param repository
	 * @param groupId
	 * @throws DataException
	 */
	public Group(Repository repository, int groupId) throws DataException {
		this.repository = repository;
		this.groupRepository = repository.getGroupRepository();

		this.groupData = this.repository.getGroupRepository().fromGroupId(groupId);
	}

	// Getters / setters

	public GroupData getGroupData() {
		return this.groupData;
	}

	// Shortcuts to aid code clarity

	// Membership

	private GroupMemberData getMember(String member) throws DataException {
		return groupRepository.getMember(this.groupData.getGroupId(), member);
	}

	private boolean memberExists(String member) throws DataException {
		return groupRepository.memberExists(this.groupData.getGroupId(), member);
	}

	private void addMember(String member, long joined, byte[] reference) throws DataException {
		GroupMemberData groupMemberData = new GroupMemberData(this.groupData.getGroupId(), member, joined, reference);
		groupRepository.save(groupMemberData);
	}

	private void addMember(String member, TransactionData transactionData) throws DataException {
		this.addMember(member, transactionData.getTimestamp(), transactionData.getSignature());
	}

	private void rebuildMember(String member, byte[] reference) throws DataException {
		TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(reference);
		this.addMember(member, transactionData);
	}

	private void deleteMember(String member) throws DataException {
		groupRepository.deleteMember(this.groupData.getGroupId(), member);
	}

	// Adminship

	private GroupAdminData getAdmin(String admin) throws DataException {
		return groupRepository.getAdmin(this.groupData.getGroupId(), admin);
	}

	private boolean adminExists(String admin) throws DataException {
		return groupRepository.adminExists(this.groupData.getGroupId(), admin);
	}

	private void addAdmin(String admin, byte[] reference) throws DataException {
		GroupAdminData groupAdminData = new GroupAdminData(this.groupData.getGroupId(), admin, reference);
		groupRepository.save(groupAdminData);
	}

	private void addAdmin(String admin, TransactionData transactionData) throws DataException {
		this.addAdmin(admin, transactionData.getSignature());
	}

	private void rebuildAdmin(String admin, byte[] reference) throws DataException {
		TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(reference);
		this.addAdmin(admin, transactionData);
	}

	private void deleteAdmin(String admin) throws DataException {
		groupRepository.deleteAdmin(this.groupData.getGroupId(), admin);
	}

	// Join request

	private GroupJoinRequestData getJoinRequest(String joiner) throws DataException {
		return groupRepository.getJoinRequest(this.groupData.getGroupId(), joiner);
	}

	private void addJoinRequest(String joiner, byte[] reference) throws DataException {
		GroupJoinRequestData groupJoinRequestData = new GroupJoinRequestData(this.groupData.getGroupId(), joiner, reference);
		groupRepository.save(groupJoinRequestData);
	}

	private void rebuildJoinRequest(String joiner, byte[] reference) throws DataException {
		this.addJoinRequest(joiner, reference);
	}

	private void deleteJoinRequest(String joiner) throws DataException {
		groupRepository.deleteJoinRequest(this.groupData.getGroupId(), joiner);
	}

	// Invites

	private GroupInviteData getInvite(String invitee) throws DataException {
		return groupRepository.getInvite(this.groupData.getGroupId(), invitee);
	}

	private void addInvite(GroupInviteTransactionData groupInviteTransactionData) throws DataException {
		Account inviter = new PublicKeyAccount(this.repository, groupInviteTransactionData.getAdminPublicKey());
		String invitee = groupInviteTransactionData.getInvitee();
		Long expiry = null;
		int timeToLive = groupInviteTransactionData.getTimeToLive();
		if (timeToLive != 0)
			expiry = groupInviteTransactionData.getTimestamp() + timeToLive * 1000;

		GroupInviteData groupInviteData = new GroupInviteData(this.groupData.getGroupId(), inviter.getAddress(), invitee, expiry,
				groupInviteTransactionData.getSignature());
		groupRepository.save(groupInviteData);
	}

	private void rebuildInvite(String invitee, byte[] reference) throws DataException {
		TransactionData previousTransactionData = this.repository.getTransactionRepository().fromSignature(reference);
		this.addInvite((GroupInviteTransactionData) previousTransactionData);
	}

	private void deleteInvite(String invitee) throws DataException {
		groupRepository.deleteInvite(this.groupData.getGroupId(), invitee);
	}

	// Bans

	private void addBan(GroupBanTransactionData groupBanTransactionData) throws DataException {
		String offender = groupBanTransactionData.getOffender();
		Account admin = new PublicKeyAccount(this.repository, groupBanTransactionData.getAdminPublicKey());
		long banned = groupBanTransactionData.getTimestamp();
		String reason = groupBanTransactionData.getReason();

		Long expiry = null;
		int timeToLive = groupBanTransactionData.getTimeToLive();
		if (timeToLive != 0)
			expiry = groupBanTransactionData.getTimestamp() + timeToLive * 1000;

		// Save reference to this banning transaction so cancel-ban can rebuild ban during orphaning.
		byte[] reference = groupBanTransactionData.getSignature();

		GroupBanData groupBanData = new GroupBanData(this.groupData.getGroupId(), offender, admin.getAddress(), banned, reason, expiry, reference);
		groupRepository.save(groupBanData);
	}

	// Processing

	// "un"-methods are the orphaning versions. e.g. "uncreate" undoes "create" processing.

	/*
	 * GroupData records can be changed by CREATE_GROUP or UPDATE_GROUP transactions.
	 * 
	 * GroupData stores the signature of the last transaction that caused a change to its contents
	 * in a field called "reference".
	 * 
	 * During orphaning, "reference" is used to fetch the previous GroupData-changing transaction
	 * and that transaction's contents are used to restore the previous GroupData state,
	 * including GroupData's previous "reference" value.
	 */

	// CREATE GROUP

	public void create(CreateGroupTransactionData createGroupTransactionData) throws DataException {
		// Note: this.groupData already populated by our constructor above
		this.repository.getGroupRepository().save(this.groupData);

		// Add owner as member
		this.addMember(this.groupData.getOwner(), createGroupTransactionData);

		// Add owner as admin
		this.addAdmin(this.groupData.getOwner(), createGroupTransactionData);
	}

	public void uncreate() throws DataException {
		// Repository takes care of cleaning up ancilliary data!
		this.repository.getGroupRepository().delete(this.groupData.getGroupId());
	}

	// UPDATE GROUP

	/*
	 * In UPDATE_GROUP transactions we store the current GroupData's "reference" in the
	 * transaction's field "group_reference" and update GroupData's "reference" to
	 * our transaction's signature to form an undo chain.
	 */

	public void updateGroup(UpdateGroupTransactionData updateGroupTransactionData) throws DataException {
		// Save GroupData's reference in our transaction data
		updateGroupTransactionData.setGroupReference(this.groupData.getReference());

		// Update GroupData's reference to this transaction's signature
		this.groupData.setReference(updateGroupTransactionData.getSignature());

		// Update Group's owner and description
		this.groupData.setOwner(updateGroupTransactionData.getNewOwner());
		this.groupData.setDescription(updateGroupTransactionData.getNewDescription());
		this.groupData.setIsOpen(updateGroupTransactionData.getNewIsOpen());
		this.groupData.setApprovalThreshold(updateGroupTransactionData.getNewApprovalThreshold());
		this.groupData.setUpdated(updateGroupTransactionData.getTimestamp());

		// Save updated group data
		groupRepository.save(this.groupData);

		String newOwner = updateGroupTransactionData.getNewOwner();

		// New owner should be a member if not already
		if (!this.memberExists(newOwner))
			this.addMember(newOwner, updateGroupTransactionData);

		// New owner should be an admin if not already
		if (!this.adminExists(newOwner))
			this.addAdmin(newOwner, updateGroupTransactionData);

		// Previous owner retained as admin and member
	}

	public void unupdateGroup(UpdateGroupTransactionData updateGroupTransactionData) throws DataException {
		// Previous group reference is taken from this transaction's cached copy
		this.groupData.setReference(updateGroupTransactionData.getGroupReference());

		// Previous Group's owner and/or description taken from referenced transaction
		this.revertGroupUpdate();

		// Save reverted group data
		groupRepository.save(this.groupData);

		// If ownership changed we need to do more work. Note groupData's owner is reverted at this point.
		String newOwner = updateGroupTransactionData.getNewOwner();

		if (!this.groupData.getOwner().equals(newOwner)) {
			// If this update caused [what was] new owner to become admin, then revoke that now.
			// (It's possible they were an admin prior to being given ownership so we need to retain that).
			GroupAdminData groupAdminData = this.getAdmin(newOwner);
			if (Arrays.equals(groupAdminData.getReference(), updateGroupTransactionData.getSignature()))
				this.deleteAdmin(newOwner);

			// If this update caused [what was] new owner to become member, then revoke that now.
			// (It's possible they were a member prior to being given ownership so we need to retain that).
			GroupMemberData groupMemberData = this.getMember(newOwner);
			if (Arrays.equals(groupMemberData.getReference(), updateGroupTransactionData.getSignature()))
				this.deleteMember(newOwner);
		}
	}

	/** Reverts groupData using previous values stored in referenced transaction. */
	private void revertGroupUpdate() throws DataException {
		TransactionData previousTransactionData = this.repository.getTransactionRepository().fromSignature(this.groupData.getReference());
		if (previousTransactionData == null)
			throw new DataException("Unable to revert group transaction as referenced transaction not found in repository");

		switch (previousTransactionData.getType()) {
			case CREATE_GROUP:
				CreateGroupTransactionData previousCreateGroupTransactionData = (CreateGroupTransactionData) previousTransactionData;
				this.groupData.setOwner(previousCreateGroupTransactionData.getOwner());
				this.groupData.setDescription(previousCreateGroupTransactionData.getDescription());
				this.groupData.setIsOpen(previousCreateGroupTransactionData.getIsOpen());
				this.groupData.setApprovalThreshold(previousCreateGroupTransactionData.getApprovalThreshold());
				this.groupData.setUpdated(null);
				break;

			case UPDATE_GROUP:
				UpdateGroupTransactionData previousUpdateGroupTransactionData = (UpdateGroupTransactionData) previousTransactionData;
				this.groupData.setOwner(previousUpdateGroupTransactionData.getNewOwner());
				this.groupData.setDescription(previousUpdateGroupTransactionData.getNewDescription());
				this.groupData.setIsOpen(previousUpdateGroupTransactionData.getNewIsOpen());
				this.groupData.setApprovalThreshold(previousUpdateGroupTransactionData.getNewApprovalThreshold());
				this.groupData.setUpdated(previousUpdateGroupTransactionData.getTimestamp());
				break;

			default:
				throw new IllegalStateException("Unable to revert group transaction due to unsupported referenced transaction");
		}

		// Previous owner will still be admin and member at this point
	}

	public void promoteToAdmin(AddGroupAdminTransactionData addGroupAdminTransactionData) throws DataException {
		this.addAdmin(addGroupAdminTransactionData.getMember(), addGroupAdminTransactionData.getSignature());
	}

	public void unpromoteToAdmin(AddGroupAdminTransactionData addGroupAdminTransactionData) throws DataException {
		this.deleteAdmin(addGroupAdminTransactionData.getMember());
	}

	public void demoteFromAdmin(RemoveGroupAdminTransactionData removeGroupAdminTransactionData) throws DataException {
		String admin = removeGroupAdminTransactionData.getAdmin();

		// Save reference to the transaction that caused adminship so we can revert if orphaning.
		GroupAdminData groupAdminData = this.getAdmin(admin);
		// Reference now part of this transaction but actually saved into repository by caller.
		removeGroupAdminTransactionData.setAdminReference(groupAdminData.getReference());

		// Demote
		this.deleteAdmin(admin);
	}

	public void undemoteFromAdmin(RemoveGroupAdminTransactionData removeGroupAdminTransactionData) throws DataException {
		String admin = removeGroupAdminTransactionData.getAdmin();

		// Rebuild admin entry using stored reference to transaction that causes adminship
		this.rebuildAdmin(admin, removeGroupAdminTransactionData.getAdminReference());

		// Clean cached reference in this transaction
		removeGroupAdminTransactionData.setAdminReference(null);
	}

	public void kick(GroupKickTransactionData groupKickTransactionData) throws DataException {
		String member = groupKickTransactionData.getMember();

		// If there is a pending join request then this is a essentially a deny response so delete join request and exit
		GroupJoinRequestData groupJoinRequestData = this.getJoinRequest(member);
		if (groupJoinRequestData != null) {
			// Save reference to the transaction that created join request so we can rebuild join request during orphaning.
			groupKickTransactionData.setJoinReference(groupJoinRequestData.getReference());

			// Delete join request
			this.deleteJoinRequest(member);

			// Make sure kick transaction's member/admin-references are null to indicate that there
			// was no existing member but actually only a join request. This should prevent orphaning code
			// from trying to incorrectly recreate a member/admin.
			groupKickTransactionData.setMemberReference(null);
			groupKickTransactionData.setAdminReference(null);

			return;
		} else {
			// Clear any cached join reference
			groupKickTransactionData.setJoinReference(null);
		}

		GroupAdminData groupAdminData = this.getAdmin(member);
		if (groupAdminData != null) {
			// Save reference to the transaction that caused adminship so we can rebuild adminship during orphaning.
			groupKickTransactionData.setAdminReference(groupAdminData.getReference());

			// Kicked, so no longer an admin
			this.deleteAdmin(member);
		} else {
			// Not an admin so no reference
			groupKickTransactionData.setAdminReference(null);
		}

		GroupMemberData groupMemberData = this.getMember(member);
		// Save reference to the transaction that caused membership so we can rebuild membership during orphaning.
		groupKickTransactionData.setMemberReference(groupMemberData.getReference());

		// Kicked, so no longer a member
		this.deleteMember(member);
	}

	public void unkick(GroupKickTransactionData groupKickTransactionData) throws DataException {
		String member = groupKickTransactionData.getMember();

		// If there's no cached reference to the transaction that caused membership then the kick was only a deny response to a join-request.
		byte[] joinReference = groupKickTransactionData.getJoinReference();
		if (joinReference != null) {
			// Rebuild join-request
			this.rebuildJoinRequest(member, joinReference);

			return;
		}

		// Rebuild member entry using stored transaction reference
		this.rebuildMember(member, groupKickTransactionData.getMemberReference());

		if (groupKickTransactionData.getAdminReference() != null)
			// Rebuild admin entry using stored transaction reference
			this.rebuildAdmin(member, groupKickTransactionData.getAdminReference());

		// Clean cached references to transactions used to rebuild member/admin info
		groupKickTransactionData.setMemberReference(null);
		groupKickTransactionData.setAdminReference(null);
	}

	public void ban(GroupBanTransactionData groupBanTransactionData) throws DataException {
		String offender = groupBanTransactionData.getOffender();

		// Kick if member
		if (this.memberExists(offender)) {
			GroupAdminData groupAdminData = this.getAdmin(offender);
			if (groupAdminData != null) {
				// Save reference to the transaction that caused adminship so we can revert if orphaning.
				groupBanTransactionData.setAdminReference(groupAdminData.getReference());

				// Kicked, so no longer an admin
				this.deleteAdmin(offender);
			} else {
				// Not an admin so no reference
				groupBanTransactionData.setAdminReference(null);
			}

			GroupMemberData groupMemberData = this.getMember(offender);
			// Save reference to the transaction that caused membership so we can revert if orphaning.
			groupBanTransactionData.setMemberReference(groupMemberData.getReference());

			// Kicked, so no longer a member
			this.deleteMember(offender);
		} else {
			// If there is a pending join request then this is a essentially a deny response so delete join request
			GroupJoinRequestData groupJoinRequestData = this.getJoinRequest(offender);
			if (groupJoinRequestData != null) {
				// Save reference to join request so we can rebuild join request if orphaning,
				// and differentiate between needing to rebuild join request and rebuild invite.
				groupBanTransactionData.setJoinInviteReference(groupJoinRequestData.getReference());

				// Delete join request
				this.deleteJoinRequest(offender);

				// Make sure kick transaction's member/admin-references are null to indicate that there
				// was no existing member but actually only a join request. This should prevent orphaning code
				// from trying to incorrectly recreate a member/admin.
				groupBanTransactionData.setMemberReference(null);
				groupBanTransactionData.setAdminReference(null);
			} else {
				// No join request, but there could be a pending invite
				GroupInviteData groupInviteData = this.getInvite(offender);
				if (groupInviteData != null) {
					// Save reference to invite so we can rebuild invite if orphaning,
					// and differentiate between needing to rebuild join request and rebuild invite.
					groupBanTransactionData.setJoinInviteReference(groupInviteData.getReference());

					// Delete invite
					this.deleteInvite(offender);

					// Make sure kick transaction's member/admin-references are null to indicate that there
					// was no existing member but actually only a join request. This should prevent orphaning code
					// from trying to incorrectly recreate a member/admin.
					groupBanTransactionData.setMemberReference(null);
					groupBanTransactionData.setAdminReference(null);
				}
			}
		}

		// Create ban
		this.addBan(groupBanTransactionData);
	}

	public void unban(GroupBanTransactionData groupBanTransactionData) throws DataException {
		// Orphaning version of "ban" - not "cancel-ban"!
		String offender = groupBanTransactionData.getOffender();

		// Delete ban
		groupRepository.deleteBan(this.groupData.getGroupId(), offender);

		// If member was kicked as part of ban then reinstate
		if (groupBanTransactionData.getMemberReference() != null) {
			this.rebuildMember(offender, groupBanTransactionData.getMemberReference());

			if (groupBanTransactionData.getAdminReference() != null)
				// Rebuild admin entry using stored transaction reference
				this.rebuildAdmin(offender, groupBanTransactionData.getAdminReference());
		} else {
			// Do we need to reinstate pending invite or join-request?
			byte[] groupReference = groupBanTransactionData.getJoinInviteReference();
			if (groupReference != null) {
				TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(groupReference);

				switch (transactionData.getType()) {
					case GROUP_INVITE:
						// Reinstate invite
						this.rebuildInvite(offender, groupReference);
						break;

					case JOIN_GROUP:
						// Rebuild join-request
						this.rebuildJoinRequest(offender, groupReference);
						break;

					default:
						throw new IllegalStateException("Unable to revert group transaction due to unsupported referenced transaction");
				}
			}
		}
	}

	public void cancelBan(CancelGroupBanTransactionData groupUnbanTransactionData) throws DataException {
		String member = groupUnbanTransactionData.getMember();

		GroupBanData groupBanData = groupRepository.getBan(this.groupData.getGroupId(), member);

		// Save reference to banning transaction for orphaning purposes
		groupUnbanTransactionData.setBanReference(groupBanData.getReference());

		// Delete ban
		groupRepository.deleteBan(this.groupData.getGroupId(), member);
	}

	public void uncancelBan(CancelGroupBanTransactionData groupUnbanTransactionData) throws DataException {
		// Reinstate ban using cached reference to banning transaction, stored in our transaction
		TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(groupUnbanTransactionData.getBanReference());
		this.addBan((GroupBanTransactionData) transactionData);

		// Clear cached reference to banning transaction
		groupUnbanTransactionData.setBanReference(null);
	}

	public void invite(GroupInviteTransactionData groupInviteTransactionData) throws DataException {
		String invitee = groupInviteTransactionData.getInvitee();

		// If there is a pending "join request" then add new group member
		GroupJoinRequestData groupJoinRequestData = this.getJoinRequest(invitee);
		if (groupJoinRequestData != null) {
			this.addMember(invitee, groupInviteTransactionData);

			// Save reference to transaction that created join request so we can rebuild join request during orphaning.
			groupInviteTransactionData.setJoinReference(groupJoinRequestData.getReference());

			// Delete join request
			this.deleteJoinRequest(invitee);

			return;
		}

		this.addInvite(groupInviteTransactionData);
	}

	public void uninvite(GroupInviteTransactionData groupInviteTransactionData) throws DataException {
		String invitee = groupInviteTransactionData.getInvitee();

		// If member exists then they were added when invite matched join request
		if (this.memberExists(invitee)) {
			// Rebuild join request using cached reference to transaction that created join request.
			this.rebuildJoinRequest(invitee, groupInviteTransactionData.getJoinReference());

			// Delete member
			this.deleteMember(invitee);

			// Clear cached reference
			groupInviteTransactionData.setJoinReference(null);
		}

		// Delete invite
		this.deleteInvite(invitee);
	}

	public void cancelInvite(CancelGroupInviteTransactionData cancelGroupInviteTransactionData) throws DataException {
		String invitee = cancelGroupInviteTransactionData.getInvitee();

		// Save reference to invite transaction so invite can be rebuilt during orphaning.
		GroupInviteData groupInviteData = this.getInvite(invitee);
		cancelGroupInviteTransactionData.setInviteReference(groupInviteData.getReference());

		// Delete invite
		this.deleteInvite(invitee);
	}

	public void uncancelInvite(CancelGroupInviteTransactionData cancelGroupInviteTransactionData) throws DataException {
		// Reinstate invite
		TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(cancelGroupInviteTransactionData.getInviteReference());
		this.addInvite((GroupInviteTransactionData) transactionData);

		// Clear cached reference to invite transaction
		cancelGroupInviteTransactionData.setInviteReference(null);
	}

	public void join(JoinGroupTransactionData joinGroupTransactionData) throws DataException {
		Account joiner = new PublicKeyAccount(this.repository, joinGroupTransactionData.getJoinerPublicKey());

		// Any pending invite?
		GroupInviteData groupInviteData = this.getInvite(joiner.getAddress());

		// If there is no invites and this group is "closed" (i.e. invite-only) then
		// this is now a pending "join request"
		if (groupInviteData == null && !groupData.getIsOpen()) {
			// Save join request
			this.addJoinRequest(joiner.getAddress(), joinGroupTransactionData.getSignature());

			// Clear any reference to invite transaction to prevent invite rebuild during orphaning.
			joinGroupTransactionData.setInviteReference(null);

			return;
		}

		// Any invite?
		if (groupInviteData != null) {
			// Save reference to invite transaction so invite can be rebuilt during orphaning.
			joinGroupTransactionData.setInviteReference(groupInviteData.getReference());

			// Delete invite
			this.deleteInvite(joiner.getAddress());
		} else {
			// Clear any reference to invite transaction to prevent invite rebuild during orphaning.
			joinGroupTransactionData.setInviteReference(null);
		}

		// Actually add new member to group
		this.addMember(joiner.getAddress(), joinGroupTransactionData);
	}

	public void unjoin(JoinGroupTransactionData joinGroupTransactionData) throws DataException {
		Account joiner = new PublicKeyAccount(this.repository, joinGroupTransactionData.getJoinerPublicKey());

		byte[] inviteReference = joinGroupTransactionData.getInviteReference();

		// Was this a join-request only?
		if (inviteReference == null && !groupData.getIsOpen()) {
			// Delete join request
			this.deleteJoinRequest(joiner.getAddress());

			return;
		}

		// Any invite to rebuild?
		if (inviteReference != null) {
			// Rebuild invite using cache reference to invite transaction
			TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(inviteReference);
			this.addInvite((GroupInviteTransactionData) transactionData);

			// Clear cached reference to invite transaction
			joinGroupTransactionData.setInviteReference(null);
		}

		// Delete member
		this.deleteMember(joiner.getAddress());
	}

	public void leave(LeaveGroupTransactionData leaveGroupTransactionData) throws DataException {
		Account leaver = new PublicKeyAccount(this.repository, leaveGroupTransactionData.getLeaverPublicKey());

		// Potentially record reference to transaction that restores previous admin state.
		// Owners can't leave as that would leave group ownerless and in unrecoverable state.

		// Owners are also admins, so skip if owner
		if (!leaver.getAddress().equals(this.groupData.getOwner())) {
			// Fetch admin data for leaver
			GroupAdminData groupAdminData = this.getAdmin(leaver.getAddress());

			if (groupAdminData != null) {
				// Save reference to transaction that caused adminship in our transaction so we can rebuild adminship during orphaning.
				leaveGroupTransactionData.setAdminReference(groupAdminData.getReference());

				// Remove as admin
				this.deleteAdmin(leaver.getAddress());
			}
		}

		// Save reference to transaction that caused membership in our transaction so we can rebuild membership during orphaning.
		GroupMemberData groupMemberData = this.getMember(leaver.getAddress());
		leaveGroupTransactionData.setMemberReference(groupMemberData.getReference());

		// Remove as member
		this.deleteMember(leaver.getAddress());
	}

	public void unleave(LeaveGroupTransactionData leaveGroupTransactionData) throws DataException {
		Account leaver = new PublicKeyAccount(this.repository, leaveGroupTransactionData.getLeaverPublicKey());

		// Restore membership using cached reference to transaction that caused membership
		this.rebuildMember(leaver.getAddress(), leaveGroupTransactionData.getMemberReference());

		byte[] adminTransactionSignature = leaveGroupTransactionData.getAdminReference();
		if (adminTransactionSignature != null)
			// Restore adminship using cached reference to transaction that caused adminship
			this.rebuildAdmin(leaver.getAddress(), adminTransactionSignature);
	}

}
