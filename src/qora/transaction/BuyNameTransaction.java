package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Utf8;

import data.naming.NameData;
import data.transaction.BuyNameTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.naming.Name;
import repository.DataException;
import repository.Repository;

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
			amount = amount.subtract(this.transactionData.getFee());

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

		// Check reference is correct
		if (!Arrays.equals(buyer.getLastReference(), buyNameTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (buyer.getConfirmedBalance(Asset.QORA).compareTo(buyNameTransactionData.getFee()) == -1)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Update Name
		Name name = new Name(this.repository, buyNameTransactionData.getName());
		name.buy(buyNameTransactionData);

		// Save this transaction, now with updated "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(buyNameTransactionData);

		// Update buyer's balance
		Account buyer = getBuyer();
		buyer.setConfirmedBalance(Asset.QORA, buyer.getConfirmedBalance(Asset.QORA).subtract(buyNameTransactionData.getFee()));

		// Update buyer's reference
		buyer.setLastReference(buyNameTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert name
		Name name = new Name(this.repository, buyNameTransactionData.getName());
		name.unbuy(buyNameTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(buyNameTransactionData);

		// Update buyer's balance
		Account buyer = getBuyer();
		buyer.setConfirmedBalance(Asset.QORA, buyer.getConfirmedBalance(Asset.QORA).add(buyNameTransactionData.getFee()));

		// Update buyer's reference
		buyer.setLastReference(buyNameTransactionData.getReference());
	}

}
