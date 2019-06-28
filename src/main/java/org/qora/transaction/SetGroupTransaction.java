package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.asset.Asset;
import org.qora.data.transaction.SetGroupTransactionData;
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
		// Check group exists
		if (!this.repository.getGroupRepository().groupExists(setGroupTransactionData.getDefaultGroupId()))
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account creator = getCreator();

		// Must be member of group
		if (!this.repository.getGroupRepository().memberExists(setGroupTransactionData.getDefaultGroupId(), creator.getAddress()))
			return ValidationResult.NOT_GROUP_MEMBER;

		// Check fee is positive
		if (setGroupTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

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
			previousDefaultGroupId = Group.NO_GROUP;

		setGroupTransactionData.setPreviousDefaultGroupId(previousDefaultGroupId);

		// Save this transaction with account's previous defaultGroupId value
		this.repository.getTransactionRepository().save(setGroupTransactionData);

		// Set account's new default groupID
		creator.setDefaultGroupId(setGroupTransactionData.getDefaultGroupId());
	}

	@Override
	public void orphan() throws DataException {
		// Revert
		Account creator = getCreator();

		Integer previousDefaultGroupId = setGroupTransactionData.getPreviousDefaultGroupId();
		if (previousDefaultGroupId == null)
			previousDefaultGroupId = Group.NO_GROUP;

		creator.setDefaultGroupId(previousDefaultGroupId);

		// Save this transaction with removed previous defaultGroupId value
		setGroupTransactionData.setPreviousDefaultGroupId(null);
		this.repository.getTransactionRepository().save(setGroupTransactionData);

		// Update creator's balance
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(setGroupTransactionData.getFee()));

		// Update admin's reference
		creator.setLastReference(setGroupTransactionData.getReference());
	}

}
