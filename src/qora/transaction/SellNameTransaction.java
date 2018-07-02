package qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Utf8;

import data.naming.NameData;
import data.transaction.SellNameTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.BlockChain;
import qora.naming.Name;
import repository.DataException;
import repository.Repository;

public class SellNameTransaction extends Transaction {

	// Properties
	private SellNameTransactionData sellNameTransactionData;

	// Constructors

	public SellNameTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.sellNameTransactionData = (SellNameTransactionData) this.transactionData;
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
		return new PublicKeyAccount(this.repository, this.sellNameTransactionData.getOwnerPublicKey());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check name size bounds
		int nameLength = Utf8.encodedLength(sellNameTransactionData.getName());
		if (nameLength < 1 || nameLength > Name.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check name is lowercase
		if (!sellNameTransactionData.getName().equals(sellNameTransactionData.getName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		NameData nameData = this.repository.getNameRepository().fromName(sellNameTransactionData.getName());

		// Check name exists
		if (nameData == null)
			return ValidationResult.NAME_DOES_NOT_EXIST;

		// Check name isn't currently for sale
		if (nameData.getIsForSale())
			return ValidationResult.NAME_ALREADY_FOR_SALE;

		// Check transaction's public key matches name's current owner
		Account owner = new PublicKeyAccount(this.repository, sellNameTransactionData.getOwnerPublicKey());
		if (!owner.getAddress().equals(nameData.getOwner()))
			return ValidationResult.INVALID_NAME_OWNER;

		// Check amount is positive
		if (sellNameTransactionData.getAmount().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_AMOUNT;

		// Check amount within bounds
		if (sellNameTransactionData.getAmount().compareTo(BlockChain.MAX_BALANCE) > 0)
			return ValidationResult.INVALID_AMOUNT;

		// Check fee is positive
		if (sellNameTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		if (!Arrays.equals(owner.getLastReference(), sellNameTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (owner.getConfirmedBalance(Asset.QORA).compareTo(sellNameTransactionData.getFee()) == -1)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;

	}

	@Override
	public void process() throws DataException {
		// Update Name
		Name name = new Name(this.repository, sellNameTransactionData.getName());
		name.sell(sellNameTransactionData);

		// Save this transaction, now with updated "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(sellNameTransactionData);

		// Update owner's balance
		Account owner = new PublicKeyAccount(this.repository, sellNameTransactionData.getOwnerPublicKey());
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).subtract(sellNameTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(sellNameTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert name
		Name name = new Name(this.repository, sellNameTransactionData.getName());
		name.unsell(sellNameTransactionData);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(sellNameTransactionData);

		// Update owner's balance
		Account owner = new PublicKeyAccount(this.repository, sellNameTransactionData.getOwnerPublicKey());
		owner.setConfirmedBalance(Asset.QORA, owner.getConfirmedBalance(Asset.QORA).add(sellNameTransactionData.getFee()));

		// Update owner's reference
		owner.setLastReference(sellNameTransactionData.getReference());
	}

}
