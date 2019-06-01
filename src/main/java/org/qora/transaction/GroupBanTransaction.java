package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.GroupBanTransactionData;
import org.qora.data.group.GroupData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class GroupBanTransaction extends Transaction {

	// Properties
	private GroupBanTransactionData groupBanTransactionData;

	// Constructors

	public GroupBanTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.groupBanTransactionData = (GroupBanTransactionData) this.transactionData;
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

		if (address.equals(this.getOffender().getAddress()))
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
		return new PublicKeyAccount(this.repository, this.groupBanTransactionData.getAdminPublicKey());
	}

	public Account getOffender() throws DataException {
		return new Account(this.repository, this.groupBanTransactionData.getOffender());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check offender address is valid
		if (!Crypto.isValidAddress(groupBanTransactionData.getOffender()))
			return ValidationResult.INVALID_ADDRESS;

		GroupData groupData = this.repository.getGroupRepository().fromGroupId(groupBanTransactionData.getGroupId());

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account admin = getAdmin();

		// Can't ban if not an admin
		if (!this.repository.getGroupRepository().adminExists(groupBanTransactionData.getGroupId(), admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		Account offender = getOffender();

		// Can't ban another admin unless the group owner
		if (!admin.getAddress().equals(groupData.getOwner())
				&& this.repository.getGroupRepository().adminExists(groupBanTransactionData.getGroupId(), offender.getAddress()))
			return ValidationResult.INVALID_GROUP_OWNER;

		// Check fee is positive
		if (groupBanTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check admin has enough funds
		if (admin.getConfirmedBalance(Asset.QORA).compareTo(groupBanTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, groupBanTransactionData.getGroupId());
		group.ban(groupBanTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(groupBanTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, groupBanTransactionData.getGroupId());
		group.unban(groupBanTransactionData);

		// Save this transaction with removed member/admin references
		this.repository.getTransactionRepository().save(groupBanTransactionData);

		// Update admin's balance
		Account admin = getAdmin();
		admin.setConfirmedBalance(Asset.QORA, admin.getConfirmedBalance(Asset.QORA).add(groupBanTransactionData.getFee()));

		// Update admin's reference
		admin.setLastReference(groupBanTransactionData.getReference());
	}

}
