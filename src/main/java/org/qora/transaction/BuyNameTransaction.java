package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.naming.NameData;
import org.qora.data.transaction.BuyNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.naming.Name;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

import com.google.common.base.Utf8;

public class BuyNameTransaction extends Transaction {

	// Properties
	private BuyNameTransactionData buyNameTransactionData;

	// Constructors

	public BuyNameTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.buyNameTransactionData = (BuyNameTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(new Account(this.repository, this.buyNameTransactionData.getSeller()));
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getBuyer().getAddress()))
			return true;

		if (address.equals(this.buyNameTransactionData.getSeller()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getBuyer().getAddress()))
			amount = amount.subtract(this.transactionData.getFee()).subtract(this.buyNameTransactionData.getAmount());

		if (address.equals(this.buyNameTransactionData.getSeller()))
			amount = amount.add(this.buyNameTransactionData.getAmount());

		return amount;
	}

	// Navigation

	public Account getBuyer() throws DataException {
		return new PublicKeyAccount(this.repository, this.buyNameTransactionData.getBuyerPublicKey());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check name size bounds
		int nameLength = Utf8.encodedLength(buyNameTransactionData.getName());
		if (nameLength < 1 || nameLength > Name.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check name is lowercase
		if (!buyNameTransactionData.getName().equals(buyNameTransactionData.getName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		NameData nameData = this.repository.getNameRepository().fromName(buyNameTransactionData.getName());

		// Check name exists
		if (nameData == null)
			return ValidationResult.NAME_DOES_NOT_EXIST;

		// Check name is currently for sale
		if (!nameData.getIsForSale())
			return ValidationResult.NAME_NOT_FOR_SALE;

		// Check buyer isn't trying to buy own name
		Account buyer = getBuyer();
		if (buyer.getAddress().equals(nameData.getOwner()))
			return ValidationResult.BUYER_ALREADY_OWNER;

		// Check expected seller currently owns name
		if (!buyNameTransactionData.getSeller().equals(nameData.getOwner()))
			return ValidationResult.INVALID_SELLER;

		// Check amounts agree
		if (buyNameTransactionData.getAmount().compareTo(nameData.getSalePrice()) != 0)
			return ValidationResult.INVALID_AMOUNT;

		// Check fee is positive
		if (buyNameTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check issuer has enough funds
		if (buyer.getConfirmedBalance(Asset.QORA).compareTo(buyNameTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Name
		Name name = new Name(this.repository, buyNameTransactionData.getName());
		name.buy(buyNameTransactionData);

		// Save transaction with updated "name reference" pointing to previous transaction that updated name
		this.repository.getTransactionRepository().save(buyNameTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert name
		Name name = new Name(this.repository, buyNameTransactionData.getName());
		name.unbuy(buyNameTransactionData);

		// Save this transaction, with removed "name reference"
		this.repository.getTransactionRepository().save(buyNameTransactionData);

		// Update buyer's balance
		Account buyer = getBuyer();
		buyer.setConfirmedBalance(Asset.QORA, buyer.getConfirmedBalance(Asset.QORA).add(buyNameTransactionData.getFee()));

		// Update buyer's reference
		buyer.setLastReference(buyNameTransactionData.getReference());
	}

}
