package org.qora.group;

import java.util.Arrays;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupMemberData;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.LeaveGroupTransactionData;
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

	// Constructors

	/**
	 * Construct Group business object using info from create group transaction.
	 * 
	 * @param repository
	 * @param createGroupTransactionData
	 */
	public Group(Repository repository, CreateGroupTransactionData createGroupTransactionData) {
		this.repository = repository;
		this.groupData = new GroupData(createGroupTransactionData.getOwner(),
				createGroupTransactionData.getGroupName(), createGroupTransactionData.getDescription(), createGroupTransactionData.getTimestamp(),
				createGroupTransactionData.getIsOpen(), createGroupTransactionData.getSignature());
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
		this.repository.getGroupRepository().save(new GroupAdminData(this.groupData.getGroupName(), this.groupData.getOwner(), createGroupTransactionData.getSignature()));

		// Add owner as member too
		this.repository.getGroupRepository().save(new GroupMemberData(this.groupData.getGroupName(), this.groupData.getOwner(), this.groupData.getCreated(), createGroupTransactionData.getSignature()));
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
			GroupMemberData groupMemberData = new GroupMemberData(groupName, newOwner, updateGroupTransactionData.getTimestamp(), updateGroupTransactionData.getSignature());
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

	public void join(JoinGroupTransactionData joinGroupTransactionData) throws DataException {
		Account joiner = new PublicKeyAccount(this.repository, joinGroupTransactionData.getJoinerPublicKey());

		GroupMemberData groupMemberData = new GroupMemberData(joinGroupTransactionData.getGroupName(), joiner.getAddress(), joinGroupTransactionData.getTimestamp(), joinGroupTransactionData.getSignature());
		this.repository.getGroupRepository().save(groupMemberData);
	}

	public void unjoin(JoinGroupTransactionData joinGroupTransactionData) throws DataException {
		Account joiner = new PublicKeyAccount(this.repository, joinGroupTransactionData.getJoinerPublicKey());

		this.repository.getGroupRepository().deleteMember(joinGroupTransactionData.getGroupName(), joiner.getAddress());
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
		groupRepository.save(new GroupMemberData(groupName, leaver.getAddress(), membershipTransactionData.getTimestamp(), membershipTransactionData.getSignature()));

		// Put back any admin state based on referenced group-related transaction
		byte[] adminTransactionSignature = leaveGroupTransactionData.getAdminReference();
		if (adminTransactionSignature != null) {
			GroupAdminData groupAdminData = new GroupAdminData(leaveGroupTransactionData.getGroupName(), leaver.getAddress(), adminTransactionSignature);
			groupRepository.save(groupAdminData);
		}
	}

}
