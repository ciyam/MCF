package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class CreateGroupTransaction extends Transaction {

	// Properties
	private CreateGroupTransactionData createGroupTransactionData;

	// Constructors

	public CreateGroupTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.createGroupTransactionData = (CreateGroupTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(getOwner());
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getCreator().getAddress()))
			return true;

		if (address.equals(this.getOwner().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getCreator().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getOwner() throws DataException {
		return new Account(this.repository, this.createGroupTransactionData.getOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check owner address is valid
		if (!Crypto.isValidAddress(createGroupTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check approval threshold is valid
		if (createGroupTransactionData.getApprovalThreshold() == null)
			return ValidationResult.INVALID_GROUP_APPROVAL_THRESHOLD;

		// Check group name size bounds
		int groupNameLength = Utf8.encodedLength(createGroupTransactionData.getGroupName());
		if (groupNameLength < 1 || groupNameLength > Group.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		int descriptionLength = Utf8.encodedLength(createGroupTransactionData.getDescription());
		if (descriptionLength < 1 || descriptionLength > Group.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check group name is lowercase
		if (!createGroupTransactionData.getGroupName().equals(createGroupTransactionData.getGroupName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		// Check fee is positive
		if (createGroupTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		Account creator = getCreator();

		// Check creator has enough funds
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(createGroupTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Check the group name isn't already taken
		if (this.repository.getGroupRepository().groupExists(createGroupTransactionData.getGroupName()))
			return ValidationResult.GROUP_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Create Group
		Group group = new Group(this.repository, createGroupTransactionData);
		group.create(createGroupTransactionData);

		// Note newly assigned group ID in our transaction record
		createGroupTransactionData.setGroupId(group.getGroupData().getGroupId());

		// Save this transaction with newly assigned group ID
		this.repository.getTransactionRepository().save(createGroupTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Uncreate group
		Group group = new Group(this.repository, createGroupTransactionData.getGroupId());
		group.uncreate();

		// Remove assigned group ID from transaction record
		createGroupTransactionData.setGroupId(null);

		// Save this transaction with removed group ID
		this.repository.getTransactionRepository().save(createGroupTransactionData);
	}

}
