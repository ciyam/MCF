package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class JoinGroupTransaction extends Transaction {

	// Properties
	private JoinGroupTransactionData joinGroupTransactionData;

	// Constructors

	public JoinGroupTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.joinGroupTransactionData = (JoinGroupTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getJoiner().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getJoiner().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getJoiner() throws DataException {
		return new PublicKeyAccount(this.repository, this.joinGroupTransactionData.getJoinerPublicKey());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		int groupId = joinGroupTransactionData.getGroupId();

		// Check group exists
		if (!this.repository.getGroupRepository().groupExists(groupId))
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account joiner = getJoiner();

		if (this.repository.getGroupRepository().memberExists(groupId, joiner.getAddress()))
			return ValidationResult.ALREADY_GROUP_MEMBER;

		// Check member is not banned
		if (this.repository.getGroupRepository().banExists(groupId, joiner.getAddress()))
			return ValidationResult.BANNED_FROM_GROUP;

		// Check join request doesn't already exist
		if (this.repository.getGroupRepository().joinRequestExists(groupId, joiner.getAddress()))
			return ValidationResult.JOIN_REQUEST_EXISTS;

		// Check fee is positive
		if (joinGroupTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;
		// Check creator has enough funds
		if (joiner.getConfirmedBalance(Asset.QORA).compareTo(joinGroupTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, joinGroupTransactionData.getGroupId());
		group.join(joinGroupTransactionData);

		// Save this transaction with cached references to transactions that can help restore state
		this.repository.getTransactionRepository().save(joinGroupTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, joinGroupTransactionData.getGroupId());
		group.unjoin(joinGroupTransactionData);

		// Save this transaction with removed references
		this.repository.getTransactionRepository().save(joinGroupTransactionData);
	}

}
