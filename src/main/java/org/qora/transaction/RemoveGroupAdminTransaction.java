package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.RemoveGroupAdminTransactionData;
import org.qora.data.group.GroupData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class RemoveGroupAdminTransaction extends Transaction {

	// Properties
	private RemoveGroupAdminTransactionData removeGroupAdminTransactionData;

	// Constructors

	public RemoveGroupAdminTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.removeGroupAdminTransactionData = (RemoveGroupAdminTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getOwner().getAddress()))
			return true;

		if (address.equals(this.getAdmin().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getOwner().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getOwner() throws DataException {
		return new PublicKeyAccount(this.repository, this.removeGroupAdminTransactionData.getOwnerPublicKey());
	}

	public Account getAdmin() throws DataException {
		return new Account(this.repository, this.removeGroupAdminTransactionData.getAdmin());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check group name size bounds
		int groupNameLength = Utf8.encodedLength(removeGroupAdminTransactionData.getGroupName());
		if (groupNameLength < 1 || groupNameLength > Group.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check group name is lowercase
		if (!removeGroupAdminTransactionData.getGroupName().equals(removeGroupAdminTransactionData.getGroupName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		GroupData groupData = this.repository.getGroupRepository().fromGroupName(removeGroupAdminTransactionData.getGroupName());

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account owner = getOwner();

		// Check transaction's public key matches group's current owner
		if (!owner.getAddress().equals(groupData.getOwner()))
			return ValidationResult.INVALID_GROUP_OWNER;

		Account admin = getAdmin();

		// Check member is an admin
		if (!this.repository.getGroupRepository().adminExists(removeGroupAdminTransactionData.getGroupName(), admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		// Check fee is positive
		if (removeGroupAdminTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		if (!Arrays.equals(owner.getLastReference(), removeGroupAdminTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check creator has enough funds
		if (owner.getConfirmedBalance(Asset.QORA).compareTo(removeGroupAdminTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group adminship
		Group group = new Group(this.repository, removeGroupAdminTransactionData.getGroupName());
		group.demoteFromAdmin(removeGroupAdminTransactionData);

		// Save this transaction
		this.repository.getTransactionRepository().save(removeGroupAdminTransactionData);

		// Update owner's balance
		Account owner = getOwner();
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).subtract(removeGroupAdminTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(removeGroupAdminTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert group adminship
		Group group = new Group(this.repository, removeGroupAdminTransactionData.getGroupName());
		group.undemoteFromAdmin(removeGroupAdminTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(removeGroupAdminTransactionData);

		// Update owner's balance
		Account owner = getOwner();
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).add(removeGroupAdminTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(removeGroupAdminTransactionData.getReference());
	}

}