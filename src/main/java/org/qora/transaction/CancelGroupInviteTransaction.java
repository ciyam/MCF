package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.CancelGroupInviteTransactionData;
import org.qora.data.group.GroupData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class CancelGroupInviteTransaction extends Transaction {

	// Properties
	private CancelGroupInviteTransactionData cancelGroupInviteTransactionData;

	// Constructors

	public CancelGroupInviteTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.cancelGroupInviteTransactionData = (CancelGroupInviteTransactionData) this.transactionData;
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
		return new PublicKeyAccount(this.repository, this.cancelGroupInviteTransactionData.getAdminPublicKey());
	}

	public Account getInvitee() throws DataException {
		return new Account(this.repository, this.cancelGroupInviteTransactionData.getInvitee());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check invitee address is valid
		if (!Crypto.isValidAddress(cancelGroupInviteTransactionData.getInvitee()))
			return ValidationResult.INVALID_ADDRESS;

		GroupData groupData = this.repository.getGroupRepository().fromGroupId(cancelGroupInviteTransactionData.getGroupId());

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account admin = getAdmin();

		// Check admin is actually an admin
		if (!this.repository.getGroupRepository().adminExists(cancelGroupInviteTransactionData.getGroupId(), admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		Account invitee = getInvitee();

		// Check invite exists
		if (!this.repository.getGroupRepository().inviteExists(cancelGroupInviteTransactionData.getGroupId(), invitee.getAddress()))
			return ValidationResult.INVITE_UNKNOWN;

		// Check fee is positive
		if (cancelGroupInviteTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference
		if (!Arrays.equals(admin.getLastReference(), cancelGroupInviteTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check creator has enough funds
		if (admin.getConfirmedBalance(Asset.QORA).compareTo(cancelGroupInviteTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, cancelGroupInviteTransactionData.getGroupId());
		group.cancelInvite(cancelGroupInviteTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(cancelGroupInviteTransactionData);

		// Update admin's balance
		Account admin = getAdmin();
		admin.setConfirmedBalance(Asset.QORA, admin.getConfirmedBalance(Asset.QORA).subtract(cancelGroupInviteTransactionData.getFee()));

		// Update admin's reference
		admin.setLastReference(cancelGroupInviteTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, cancelGroupInviteTransactionData.getGroupId());
		group.uncancelInvite(cancelGroupInviteTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(cancelGroupInviteTransactionData);

		// Update admin's balance
		Account admin = getAdmin();
		admin.setConfirmedBalance(Asset.QORA, admin.getConfirmedBalance(Asset.QORA).add(cancelGroupInviteTransactionData.getFee()));

		// Update admin's reference
		admin.setLastReference(cancelGroupInviteTransactionData.getReference());
	}

}
