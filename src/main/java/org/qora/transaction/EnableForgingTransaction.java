package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.transaction.EnableForgingTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class EnableForgingTransaction extends Transaction {

	public static final int TIER1_MIN_FORGED_BLOCKS = 50;
	public static final int TIER1_MAX_ENABLED_ACCOUNTS = 5;

	public static final int TIER2_MIN_FORGED_BLOCKS = 5;
	public static final int TIER2_MAX_ENABLED_ACCOUNTS = 5;

	// Properties
	private EnableForgingTransactionData enableForgingTransactionData;

	// Constructors

	public EnableForgingTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.enableForgingTransactionData = (EnableForgingTransactionData) this.transactionData;
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
		return new Account(this.repository, this.enableForgingTransactionData.getTarget());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		PublicKeyAccount creator = getCreator();

		// Creator needs to have at least one forging-enabled account flag set
		Integer creatorFlags = creator.getFlags();
		if (creatorFlags == null)
			return ValidationResult.INVALID_ADDRESS;

		if ((creatorFlags & Account.FORGING_MASK) == 0)
			return ValidationResult.NO_FORGING_PERMISSION;

		// Tier3 forgers can't enable further accounts
		if ((creatorFlags & Account.TIER3_FORGING_MASK) != 0)
			return ValidationResult.FORGING_ENABLE_LIMIT;

		Account target = getTarget();

		// Target needs to NOT have ANY forging-enabled account flags set
		Integer targetFlags = target.getFlags();
		if (targetFlags != null && (targetFlags & Account.FORGING_MASK) != 0)
			return ValidationResult.FORGING_ALREADY_ENABLED;

		// Has creator reached minimum requirements?
		int numberForgedBlocks = this.repository.getBlockRepository().countForgedBlocks(creator.getPublicKey());
		int numberEnabledAccounts = this.repository.getAccountRepository().countForgingAccountsEnabledByAddress(creator.getAddress());

		if ((creatorFlags & Account.TIER1_FORGING_MASK) != 0) {
			// Tier1: minimum 2,500 forged blocks & max 50 accounts
			if (numberForgedBlocks < TIER1_MIN_FORGED_BLOCKS)
				return ValidationResult.FORGE_MORE_BLOCKS;

			if (numberEnabledAccounts >= TIER1_MAX_ENABLED_ACCOUNTS)
				return ValidationResult.FORGING_ENABLE_LIMIT;
		} else if ((creatorFlags & Account.TIER2_FORGING_MASK) != 0) {
			// Tier2: minimum 50 forged blocks & max 50 accounts
			if (numberForgedBlocks < TIER2_MIN_FORGED_BLOCKS)
				return ValidationResult.FORGE_MORE_BLOCKS;

			if (numberEnabledAccounts >= TIER2_MAX_ENABLED_ACCOUNTS)
				return ValidationResult.FORGING_ENABLE_LIMIT;
		}

		// Check fee is zero or positive
		if (enableForgingTransactionData.getFee().compareTo(BigDecimal.ZERO) < 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference
		if (!Arrays.equals(creator.getLastReference(), enableForgingTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check creator has enough funds
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(enableForgingTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		Account creator = getCreator();

		int creatorFlags = creator.getFlags();

		int forgeBit = 0;

		if ((creatorFlags & Account.TIER1_FORGING_MASK) != 0)
			forgeBit = Account.TIER2_FORGING_MASK;
		else
			forgeBit = Account.TIER3_FORGING_MASK;

		Account target = getTarget();
		Integer targetFlags = target.getFlags();
		if (targetFlags == null)
			targetFlags = 0;

		targetFlags |= forgeBit;
		
		target.setFlags(targetFlags);
		target.setForgingEnabler(creator.getAddress());

		// Save this transaction
		this.repository.getTransactionRepository().save(enableForgingTransactionData);

		// Update creator's balance
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).subtract(enableForgingTransactionData.getFee()));

		// Update creator's reference
		creator.setLastReference(enableForgingTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert
		Account creator = getCreator();

		int creatorFlags = creator.getFlags();

		int forgeBit = 0;

		if ((creatorFlags & Account.TIER1_FORGING_MASK) != 0)
			forgeBit = Account.TIER2_FORGING_MASK;
		else
			forgeBit = Account.TIER3_FORGING_MASK;

		Account target = getTarget();

		int targetFlags = target.getFlags();

		targetFlags &= ~forgeBit;

		target.setFlags(targetFlags);
		target.setForgingEnabler(null);

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(enableForgingTransactionData);

		// Update creator's balance
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(enableForgingTransactionData.getFee()));

		// Update creator's reference
		creator.setLastReference(enableForgingTransactionData.getReference());
	}

}
