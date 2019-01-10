package org.qora.group;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupMemberData;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.repository.DataException;
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

	public void create() throws DataException {
		this.repository.getGroupRepository().save(this.groupData);

		// Add owner as admin too
		this.repository.getGroupRepository().save(new GroupAdminData(this.groupData.getGroupName(), this.groupData.getOwner()));

		// Add owner as member too
		this.repository.getGroupRepository().save(new GroupMemberData(this.groupData.getGroupName(), this.groupData.getOwner(), this.groupData.getCreated()));
	}

	public void uncreate() throws DataException {
		// Repository takes care of cleaning up ancilliary data!
		this.repository.getGroupRepository().delete(this.groupData.getGroupName());
	}

	private void revert() throws DataException {
		TransactionData previousTransactionData = this.repository.getTransactionRepository().fromSignature(this.groupData.getReference());
		if (previousTransactionData == null)
			throw new DataException("Unable to revert group transaction as referenced transaction not found in repository");

		// XXX needs code to reinstate owner as admin and member

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
	}

	public void update(UpdateGroupTransactionData updateGroupTransactionData) throws DataException {
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
		this.repository.getGroupRepository().save(this.groupData);

		// XXX new owner should be an admin if not already
		// XXX new owner should be a member if not already

		// XXX what happens to previous owner? retained as admin?
	}

	public void revert(UpdateGroupTransactionData updateGroupTransactionData) throws DataException {
		// Previous group reference is taken from this transaction's cached copy
		this.groupData.setReference(updateGroupTransactionData.getGroupReference());

		// Previous Group's owner and/or description taken from referenced transaction
		this.revert();

		// Save reverted group data
		this.repository.getGroupRepository().save(this.groupData);
	}

	public void join(JoinGroupTransactionData joinGroupTransactionData) throws DataException {
		Account joiner = new PublicKeyAccount(this.repository, joinGroupTransactionData.getJoinerPublicKey());

		GroupMemberData groupMemberData = new GroupMemberData(joinGroupTransactionData.getGroupName(), joiner.getAddress(), joinGroupTransactionData.getTimestamp());
		this.repository.getGroupRepository().save(groupMemberData);
	}

	public void unjoin(JoinGroupTransactionData joinGroupTransactionData) throws DataException {
		Account joiner = new PublicKeyAccount(this.repository, joinGroupTransactionData.getJoinerPublicKey());

		GroupMemberData groupMemberData = new GroupMemberData(joinGroupTransactionData.getGroupName(), joiner.getAddress(), joinGroupTransactionData.getTimestamp());
		this.repository.getGroupRepository().delete(groupMemberData);
	}

}
