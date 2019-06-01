package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.GroupInviteTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

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
		int groupId = groupInviteTransactionData.getGroupId();

		// Check time to live zero (infinite) or positive
		if (groupInviteTransactionData.getTimeToLive() < 0)
			return ValidationResult.INVALID_LIFETIME;

		// Check member address is valid
		if (!Crypto.isValidAddress(groupInviteTransactionData.getInvitee()))
			return ValidationResult.INVALID_ADDRESS;

		// Check group exists
		if (!this.repository.getGroupRepository().groupExists(groupId))
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account admin = getAdmin();

		// Can't invite if not an admin
		if (!this.repository.getGroupRepository().adminExists(groupId, admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		Account invitee = getInvitee();

		// Check invitee not already in group
		if (this.repository.getGroupRepository().memberExists(groupId, invitee.getAddress()))
			return ValidationResult.ALREADY_GROUP_MEMBER;

		// Check invitee is not banned
		if (this.repository.getGroupRepository().banExists(groupId, invitee.getAddress()))
			return ValidationResult.BANNED_FROM_GROUP;

		// Check fee is positive
		if (groupInviteTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check creator has enough funds
		if (admin.getConfirmedBalance(Asset.QORA).compareTo(groupInviteTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, groupInviteTransactionData.getGroupId());
		group.invite(groupInviteTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(groupInviteTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, groupInviteTransactionData.getGroupId());
		group.uninvite(groupInviteTransactionData);

		// Save this transaction with removed member/admin references
		this.repository.getTransactionRepository().save(groupInviteTransactionData);

		// Update admin's balance
		Account admin = getAdmin();
		admin.setConfirmedBalance(Asset.QORA, admin.getConfirmedBalance(Asset.QORA).add(groupInviteTransactionData.getFee()));

		// Update admin's reference
		admin.setLastReference(groupInviteTransactionData.getReference());
	}

}
