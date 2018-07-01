package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import data.transaction.RegisterNameTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.crypto.Crypto;
import qora.naming.Name;
import repository.DataException;
import repository.Repository;

public class RegisterNameTransaction extends Transaction {

	// Properties
	private RegisterNameTransactionData registerNameTransactionData;

	// Constructors

	public RegisterNameTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.registerNameTransactionData = (RegisterNameTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(getOwner());
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getRegistrant().getAddress()))
			return true;

		if (address.equals(this.getOwner().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getRegistrant().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getRegistrant() throws DataException {
		return new PublicKeyAccount(this.repository, this.registerNameTransactionData.getRegistrantPublicKey());
	}

	public Account getOwner() throws DataException {
		return new Account(this.repository, this.registerNameTransactionData.getOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check owner address is valid
		if (!Crypto.isValidAddress(registerNameTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		if (registerNameTransactionData.getName().length() < 1 || registerNameTransactionData.getName().length() > Name.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check value size bounds
		if (registerNameTransactionData.getData().length() < 1 || registerNameTransactionData.getData().length() > Name.MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check name is lowercase
		if (!registerNameTransactionData.getName().equals(registerNameTransactionData.getName().toLowerCase()))
			return ValidationResult.NAME_NOT_LOWER_CASE;

		// Check the name isn't already taken
		if (this.repository.getNameRepository().nameExists(registerNameTransactionData.getName()))
			return ValidationResult.NAME_ALREADY_REGISTERED;

		// Check fee is positive
		if (registerNameTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		PublicKeyAccount registrant = new PublicKeyAccount(this.repository, registerNameTransactionData.getRegistrantPublicKey());

		if (!Arrays.equals(registrant.getLastReference(), registerNameTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (registrant.getConfirmedBalance(Asset.QORA).compareTo(registerNameTransactionData.getFee()) == -1)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Register Name
		Name name = new Name(this.repository, registerNameTransactionData);
		name.register();

		// Save this transaction
		this.repository.getTransactionRepository().save(registerNameTransactionData);

		// Update registrant's balance
		Account registrant = new PublicKeyAccount(this.repository, registerNameTransactionData.getRegistrantPublicKey());
		registrant.setConfirmedBalance(Asset.QORA, registrant.getConfirmedBalance(Asset.QORA).subtract(registerNameTransactionData.getFee()));

		// Update registrant's reference
		registrant.setLastReference(registerNameTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Unregister name
		Name name = new Name(this.repository, registerNameTransactionData.getName());
		name.unregister();

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(registerNameTransactionData);

		// Update registrant's balance
		Account registrant = new PublicKeyAccount(this.repository, registerNameTransactionData.getRegistrantPublicKey());
		registrant.setConfirmedBalance(Asset.QORA, registrant.getConfirmedBalance(Asset.QORA).add(registerNameTransactionData.getFee()));

		// Update registrant's reference
		registrant.setLastReference(registerNameTransactionData.getReference());
	}

}
