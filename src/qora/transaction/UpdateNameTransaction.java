package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Utf8;

import data.transaction.UpdateNameTransactionData;
import data.naming.NameData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.crypto.Crypto;
import qora.naming.Name;
import repository.DataException;
import repository.Repository;

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

		// Check name isn't currently for sale
		if (nameData.getIsForSale())
			return ValidationResult.NAME_ALREADY_FOR_SALE;

		// Check transaction's public key matches name's current owner
		Account owner = new PublicKeyAccount(this.repository, updateNameTransactionData.getOwnerPublicKey());
		if (!owner.getAddress().equals(nameData.getOwner()))
			return ValidationResult.INVALID_NAME_OWNER;

		// Check fee is positive
		if (updateNameTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		if (!Arrays.equals(owner.getLastReference(), updateNameTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (owner.getConfirmedBalance(Asset.QORA).compareTo(updateNameTransactionData.getFee()) == -1)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Name
		Name name = new Name(this.repository, updateNameTransactionData.getName());
		name.update(updateNameTransactionData);

		// Save this transaction, now with updated "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(updateNameTransactionData);

		// Update owner's balance
		Account owner = new PublicKeyAccount(this.repository, updateNameTransactionData.getOwnerPublicKey());
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).subtract(updateNameTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(updateNameTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert name
		Name name = new Name(this.repository, updateNameTransactionData.getName());
		name.revert(updateNameTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(updateNameTransactionData);

		// Update owner's balance
		Account owner = new PublicKeyAccount(this.repository, updateNameTransactionData.getOwnerPublicKey());
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).add(updateNameTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(updateNameTransactionData.getReference());
	}

}
