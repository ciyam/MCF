package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.transaction.AddGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class AddGroupAdminTransaction extends Transaction {

	// Properties
	private AddGroupAdminTransactionData addGroupAdminTransactionData;

	// Constructors

	public AddGroupAdminTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.addGroupAdminTransactionData = (AddGroupAdminTransactionData) this.transactionData;
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

		if (address.equals(this.getMember().getAddress()))
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
		return new PublicKeyAccount(this.repository, this.addGroupAdminTransactionData.getOwnerPublicKey());
	}

	public Account getMember() throws DataException {
		return new Account(this.repository, this.addGroupAdminTransactionData.getMember());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check member address is valid
		if (!Crypto.isValidAddress(addGroupAdminTransactionData.getMember()))
			return ValidationResult.INVALID_ADDRESS;

		// Check group exists
		if (!this.repository.getGroupRepository().groupExists(addGroupAdminTransactionData.getGroupId()))
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		// Check fee is positive
		if (addGroupAdminTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		Account owner = getOwner();
		String groupOwner = this.repository.getGroupRepository().getOwner(addGroupAdminTransactionData.getGroupId());

		// Check transaction's public key matches group's current owner
		if (!owner.getAddress().equals(groupOwner))
			return ValidationResult.INVALID_GROUP_OWNER;

		Account member = getMember();

		// Check address is a member
		if (!this.repository.getGroupRepository().memberExists(addGroupAdminTransactionData.getGroupId(), member.getAddress()))
			return ValidationResult.NOT_GROUP_MEMBER;

		// Check member is not already an admin
		if (this.repository.getGroupRepository().adminExists(addGroupAdminTransactionData.getGroupId(), member.getAddress()))
			return ValidationResult.ALREADY_GROUP_ADMIN;

		// Check group owner has enough funds
		if (owner.getConfirmedBalance(Asset.QORA).compareTo(addGroupAdminTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Group adminship
		Group group = new Group(this.repository, addGroupAdminTransactionData.getGroupId());
		group.promoteToAdmin(addGroupAdminTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group adminship
		Group group = new Group(this.repository, addGroupAdminTransactionData.getGroupId());
		group.unpromoteToAdmin(addGroupAdminTransactionData);
	}

}