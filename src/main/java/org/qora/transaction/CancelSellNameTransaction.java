package org.qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.naming.NameData;
import org.qora.data.transaction.CancelSellNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.naming.Name;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class CancelSellNameTransaction extends Transaction {

	// Properties
	private CancelSellNameTransactionData cancelSellNameTransactionData;

	// Constructors

	public CancelSellNameTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.cancelSellNameTransactionData = (CancelSellNameTransactionData) this.transactionData;

		// XXX This is horrible - thanks to JAXB unmarshalling not calling constructor
		if (this.transactionData.getCreatorPublicKey() == null)
			this.transactionData.setCreatorPublicKey(this.cancelSellNameTransactionData.getOwnerPublicKey());
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() {
		return new ArrayList<Account>();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getOwner().getAddress()))
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
		return new PublicKeyAccount(this.repository, this.cancelSellNameTransactionData.getOwnerPublicKey());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check name size bounds
		int nameLength = Utf8.encodedLength(cancelSellNameTransactionData.getName());
		if (nameLength < 1 || nameLength > Name.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check name is lowercase
		if (!cancelSellNameTransactionData.getName().equals(cancelSellNameTransactionData.getName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		NameData nameData = this.repository.getNameRepository().fromName(cancelSellNameTransactionData.getName());

		// Check name exists
		if (nameData == null)
			return ValidationResult.NAME_DOES_NOT_EXIST;

		// Check name is currently for sale
		if (!nameData.getIsForSale())
			return ValidationResult.NAME_NOT_FOR_SALE;

		// Check transaction's public key matches name's current owner
		Account owner = getOwner();
		if (!owner.getAddress().equals(nameData.getOwner()))
			return ValidationResult.INVALID_NAME_OWNER;

		// Check fee is positive
		if (cancelSellNameTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		if (!Arrays.equals(owner.getLastReference(), cancelSellNameTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (owner.getConfirmedBalance(Asset.QORA).compareTo(cancelSellNameTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;

	}

	@Override
	public void process() throws DataException {
		// Update Name
		Name name = new Name(this.repository, cancelSellNameTransactionData.getName());
		name.sell(cancelSellNameTransactionData);

		// Save this transaction, now with updated "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(cancelSellNameTransactionData);

		// Update owner's balance
		Account owner = getOwner();
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).subtract(cancelSellNameTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(cancelSellNameTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert name
		Name name = new Name(this.repository, cancelSellNameTransactionData.getName());
		name.unsell(cancelSellNameTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(cancelSellNameTransactionData);

		// Update owner's balance
		Account owner = getOwner();
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).add(cancelSellNameTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(cancelSellNameTransactionData.getReference());
	}

}
