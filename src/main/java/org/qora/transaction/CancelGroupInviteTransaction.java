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
import org.qora.repository.GroupRepository;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class CancelGroupInviteTransaction extends Transaction {

	// Properties
	private CancelGroupInviteTransactionData cancelCancelGroupInviteTransactionData;

	// Constructors

	public CancelGroupInviteTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.cancelCancelGroupInviteTransactionData = (CancelGroupInviteTransactionData) this.transactionData;
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
		return new PublicKeyAccount(this.repository, this.cancelCancelGroupInviteTransactionData.getAdminPublicKey());
	}

	public Account getInvitee() throws DataException {
		return new Account(this.repository, this.cancelCancelGroupInviteTransactionData.getInvitee());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		GroupRepository groupRepository = this.repository.getGroupRepository();
		String groupName = cancelCancelGroupInviteTransactionData.getGroupName();

		// Check member address is valid
		if (!Crypto.isValidAddress(cancelCancelGroupInviteTransactionData.getInvitee()))
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

		// Check invite exists
		if (!groupRepository.inviteExists(groupName, admin.getAddress(), invitee.getAddress()))
			return ValidationResult.INVITE_UNKNOWN;

		// Check fee is positive
		if (cancelCancelGroupInviteTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		if (!Arrays.equals(admin.getLastReference(), cancelCancelGroupInviteTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check creator has enough funds
		if (admin.getConfirmedBalance(Asset.QORA).compareTo(cancelCancelGroupInviteTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, cancelCancelGroupInviteTransactionData.getGroupName());
		group.cancelInvite(cancelCancelGroupInviteTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(cancelCancelGroupInviteTransactionData);

		// Update admin's balance
		Account admin = getAdmin();
		admin.setConfirmedBalance(Asset.QORA, admin.getConfirmedBalance(Asset.QORA).subtract(cancelCancelGroupInviteTransactionData.getFee()));

		// Update admin's reference
		admin.setLastReference(cancelCancelGroupInviteTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, cancelCancelGroupInviteTransactionData.getGroupName());
		group.uncancelInvite(cancelCancelGroupInviteTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(cancelCancelGroupInviteTransactionData);

		// Update admin's balance
		Account admin = getAdmin();
		admin.setConfirmedBalance(Asset.QORA, admin.getConfirmedBalance(Asset.QORA).add(cancelCancelGroupInviteTransactionData.getFee()));

		// Update admin's reference
		admin.setLastReference(cancelCancelGroupInviteTransactionData.getReference());
	}

}
