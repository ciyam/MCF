package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.naming.NameData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateNameTransactionData;
import org.qora.naming.Name;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class UpdateNameTransaction extends Transaction {

	// Properties
	private UpdateNameTransactionData updateNameTransactionData;

	// Constructors

	public UpdateNameTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.updateNameTransactionData = (UpdateNameTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(getNewOwner());
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getOwner().getAddress()))
			return true;

		if (address.equals(this.getNewOwner().getAddress()))
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
		return new PublicKeyAccount(this.repository, this.updateNameTransactionData.getOwnerPublicKey());
	}

	public Account getNewOwner() throws DataException {
		return new Account(this.repository, this.updateNameTransactionData.getNewOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check new owner address is valid
		if (!Crypto.isValidAddress(updateNameTransactionData.getNewOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		int nameLength = Utf8.encodedLength(updateNameTransactionData.getName());
		if (nameLength < 1 || nameLength > Name.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check new data size bounds
		int newDataLength = Utf8.encodedLength(updateNameTransactionData.getNewData());
		if (newDataLength < 1 || newDataLength > Name.MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check name is lowercase
		if (!updateNameTransactionData.getName().equals(updateNameTransactionData.getName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		NameData nameData = this.repository.getNameRepository().fromName(updateNameTransactionData.getName());

		// Check name exists
		if (nameData == null)
			return ValidationResult.NAME_DOES_NOT_EXIST;

		// As this transaction type could require approval, check txGroupId matches groupID at creation
		if (nameData.getCreationGroupId() != updateNameTransactionData.getTxGroupId())
			return ValidationResult.TX_GROUP_ID_MISMATCH;

		Account owner = getOwner();

		// Check fee is positive
		if (updateNameTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check issuer has enough funds
		if (owner.getConfirmedBalance(Asset.QORA).compareTo(updateNameTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		NameData nameData = this.repository.getNameRepository().fromName(updateNameTransactionData.getName());

		// Check name isn't currently for sale
		if (nameData.getIsForSale())
			return ValidationResult.NAME_ALREADY_FOR_SALE;

		Account owner = getOwner();

		// Check transaction's public key matches name's current owner
		if (!owner.getAddress().equals(nameData.getOwner()))
			return ValidationResult.INVALID_NAME_OWNER;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Name
		Name name = new Name(this.repository, updateNameTransactionData.getName());
		name.update(updateNameTransactionData);

		// Save this transaction, now with updated "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(updateNameTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert name
		Name name = new Name(this.repository, updateNameTransactionData.getName());
		name.revert(updateNameTransactionData);

		// Save this transaction, now with removed "name reference"
		this.repository.getTransactionRepository().save(updateNameTransactionData);

		// Update owner's balance
		Account owner = getOwner();
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).add(updateNameTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(updateNameTransactionData.getReference());
	}

}
