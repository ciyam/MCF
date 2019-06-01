package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.GenesisAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.AccountFlagsTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class AccountFlagsTransaction extends Transaction {

	// Properties
	private AccountFlagsTransactionData accountFlagsTransactionData;

	// Constructors

	public AccountFlagsTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.accountFlagsTransactionData = (AccountFlagsTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getCreator().getAddress()))
			return true;

		if (address.equals(this.getTarget().getAddress()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (address.equals(this.getCreator().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	public Account getTarget() {
		return new Account(this.repository, this.accountFlagsTransactionData.getTarget());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		Account creator = getCreator();

		// Only genesis account can modify flags
		if (!creator.getAddress().equals(new GenesisAccount(repository).getAddress()))
			return ValidationResult.NO_FLAG_PERMISSION;

		// Check fee is zero or positive
		if (accountFlagsTransactionData.getFee().compareTo(BigDecimal.ZERO) < 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check creator has enough funds
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(accountFlagsTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		Account target = getTarget();
		Integer previousFlags = target.getFlags();

		accountFlagsTransactionData.setPreviousFlags(previousFlags);

		// Save this transaction with target account's previous flags value
		this.repository.getTransactionRepository().save(accountFlagsTransactionData);

		// If account doesn't have entry in database yet (e.g. genesis block) then flags are zero
		if (previousFlags == null)
			previousFlags = 0;

		// Set account's new flags
		int newFlags = previousFlags & accountFlagsTransactionData.getAndMask()
				| accountFlagsTransactionData.getOrMask() ^ accountFlagsTransactionData.getXorMask();

		target.setFlags(newFlags);
	}

	@Override
	public void orphan() throws DataException {
		// Revert
		Account target = getTarget();

		Integer previousFlags = accountFlagsTransactionData.getPreviousFlags();

		// If previousFlags are null then account didn't exist before this transaction
		if (previousFlags == null)
			this.repository.getAccountRepository().delete(target.getAddress());
		else
			target.setFlags(previousFlags);

		// Remove previous flags from transaction itself
		accountFlagsTransactionData.setPreviousFlags(null);
		this.repository.getTransactionRepository().save(accountFlagsTransactionData);

		Account creator = getCreator();

		// Update creator's balance
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(accountFlagsTransactionData.getFee()));

		// Update creator's reference
		creator.setLastReference(accountFlagsTransactionData.getReference());
	}

}
