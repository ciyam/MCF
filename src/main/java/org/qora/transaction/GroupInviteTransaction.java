package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.GroupInviteTransactionData;
import org.qora.data.group.GroupData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.GroupRepository;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class GroupInviteTransaction extends Transaction {

	// Properties
	private GroupInviteTransactionData groupInviteTransactionData;

	// Constructors

	public GroupInviteTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.groupInviteTransactionData = (GroupInviteTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getAdmin().getAddress()))
			return true;

		if (address.equals(this.getInvitee().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getAdmin().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getAdmin() throws DataException {
		return new PublicKeyAccount(this.repository, this.groupInviteTransactionData.getAdminPublicKey());
	}

	public Account getInvitee() throws DataException {
		return new Account(this.repository, this.groupInviteTransactionData.getInvitee());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = groupInviteTransactionData.getGroupName();

		// Check time to live zero (infinite) or positive
		if (groupInviteTransactionData.getTimeToLive() < 0)
			return ValidationResult.INVALID_LIFETIME;

		// Check member address is valid
		if (!Crypto.isValidAddress(groupInviteTransactionData.getInvitee()))
			return ValidationResult.INVALID_ADDRESS;

		// Check group name size bounds
		int groupNameLength = Utf8.encodedLength(groupName);
		if (groupNameLength < 1 || groupNameLength > Group.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check group name is lowercase
		if (!groupName.equals(groupName.toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		GroupData groupData = groupRepository.fromGroupName(groupName);

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account admin = getAdmin();
		Account invitee = getInvitee();

		// Can't invite if not an admin
		if (!groupRepository.adminExists(groupName, admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		// Check invitee not already in group
		if (groupRepository.memberExists(groupName, invitee.getAddress()))
			return ValidationResult.ALREADY_GROUP_MEMBER;

		// Check fee is positive
		if (groupInviteTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		if (!Arrays.equals(admin.getLastReference(), groupInviteTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check creator has enough funds
		if (admin.getConfirmedBalance(Asset.QORA).compareTo(groupInviteTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, groupInviteTransactionData.getGroupName());
		group.invite(groupInviteTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(groupInviteTransactionData);

		// Update admin's balance
		Account admin = getAdmin();
		admin.setConfirmedBalance(Asset.QORA, admin.getConfirmedBalance(Asset.QORA).subtract(groupInviteTransactionData.getFee()));

		// Update admin's reference
		admin.setLastReference(groupInviteTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, groupInviteTransactionData.getGroupName());
		group.uninvite(groupInviteTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(groupInviteTransactionData);

		// Update admin's balance
		Account admin = getAdmin();
		admin.setConfirmedBalance(Asset.QORA, admin.getConfirmedBalance(Asset.QORA).add(groupInviteTransactionData.getFee()));

		// Update admin's reference
		admin.setLastReference(groupInviteTransactionData.getReference());
	}

}
