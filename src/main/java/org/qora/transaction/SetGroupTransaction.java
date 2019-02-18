package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.asset.Asset;
import org.qora.data.transaction.SetGroupTransactionData;
import org.qora.data.group.GroupData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class SetGroupTransaction extends Transaction {

	// Properties
	private SetGroupTransactionData setGroupTransactionData;

	// Constructors

	public SetGroupTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.setGroupTransactionData = (SetGroupTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getCreator().getAddress()))
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

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		GroupData groupData = this.repository.getGroupRepository().fromGroupId(setGroupTransactionData.getDefaultGroupId());

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account creator = getCreator();

		// Must be member of group
		if (!this.repository.getGroupRepository().memberExists(setGroupTransactionData.getDefaultGroupId(), creator.getAddress()))
			return ValidationResult.NOT_GROUP_MEMBER;

		// Check fee is positive
		if (setGroupTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference
		if (!Arrays.equals(creator.getLastReference(), setGroupTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check creator has enough funds
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(setGroupTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		Account creator = getCreator();

		Integer previousDefaultGroupId = this.repository.getAccountRepository().getDefaultGroupId(creator.getAddress());
		if (previousDefaultGroupId == null)
			previousDefaultGroupId = Group.DEFAULT_GROUP;

		setGroupTransactionData.setPreviousDefaultGroupId(previousDefaultGroupId);

		// Save this transaction with account's previous defaultGroupId value
		this.repository.getTransactionRepository().save(setGroupTransactionData);

		// Set account's new default groupID
		creator.setDefaultGroupId(setGroupTransactionData.getDefaultGroupId());

		// Update creator's balance
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).subtract(setGroupTransactionData.getFee()));

		// Update admin's reference
		creator.setLastReference(setGroupTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert
		Account creator = getCreator();

		Integer previousDefaultGroupId = setGroupTransactionData.getPreviousDefaultGroupId();
		if (previousDefaultGroupId == null)
			previousDefaultGroupId = Group.DEFAULT_GROUP;

		creator.setDefaultGroupId(previousDefaultGroupId);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(setGroupTransactionData);

		// Update creator's balance
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(setGroupTransactionData.getFee()));

		// Update admin's reference
		creator.setLastReference(setGroupTransactionData.getReference());
	}

}
