package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.group.GroupData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

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
		// Check group name size bounds
		int groupNameLength = Utf8.encodedLength(joinGroupTransactionData.getGroupName());
		if (groupNameLength < 1 || groupNameLength > Group.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check group name is lowercase
		if (!joinGroupTransactionData.getGroupName().equals(joinGroupTransactionData.getGroupName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		GroupData groupData = this.repository.getGroupRepository().fromGroupName(joinGroupTransactionData.getGroupName());

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account joiner = getJoiner();

		if (this.repository.getGroupRepository().memberExists(joinGroupTransactionData.getGroupName(), joiner.getAddress()))
			return ValidationResult.ALREADY_GROUP_MEMBER;

		// Check fee is positive
		if (joinGroupTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		if (!Arrays.equals(joiner.getLastReference(), joinGroupTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check creator has enough funds
		if (joiner.getConfirmedBalance(Asset.QORA).compareTo(joinGroupTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, joinGroupTransactionData.getGroupName());
		group.join(joinGroupTransactionData);

		// Save this transaction
		this.repository.getTransactionRepository().save(joinGroupTransactionData);

		// Update joiner's balance
		Account joiner = getJoiner();
		joiner.setConfirmedBalance(Asset.QORA, joiner.getConfirmedBalance(Asset.QORA).subtract(joinGroupTransactionData.getFee()));

		// Update joiner's reference
		joiner.setLastReference(joinGroupTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, joinGroupTransactionData.getGroupName());
		group.unjoin(joinGroupTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(joinGroupTransactionData);

		// Update joiner's balance
		Account joiner = getJoiner();
		joiner.setConfirmedBalance(Asset.QORA, joiner.getConfirmedBalance(Asset.QORA).add(joinGroupTransactionData.getFee()));

		// Update joiner's reference
		joiner.setLastReference(joinGroupTransactionData.getReference());
	}

}
