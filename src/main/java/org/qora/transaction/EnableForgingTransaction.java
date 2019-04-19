package org.qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.Forging;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.block.BlockChain.ForgingTier;
import org.qora.data.transaction.EnableForgingTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class EnableForgingTransaction extends Transaction {

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

		if ((creatorFlags & Forging.getForgingMask()) == 0)
			return ValidationResult.NO_FORGING_PERMISSION;

		int forgingTierLevel = 0;
		ForgingTier forgingTier = null;

		List<ForgingTier> forgingTiers = BlockChain.getInstance().getForgingTiers();
		for (forgingTierLevel = 0; forgingTierLevel < forgingTiers.size(); ++forgingTierLevel)
			if ((creatorFlags & (1 << forgingTierLevel)) != 0) {
				forgingTier = forgingTiers.get(forgingTierLevel);
				break;
			}

		// forgingTier should not be null at this point
		if (forgingTier == null)
			return ValidationResult.NO_FORGING_PERMISSION;

		// Final tier forgers can't enable further accounts
		if (forgingTierLevel == forgingTiers.size() - 1)
			return ValidationResult.FORGING_ENABLE_LIMIT;

		Account target = getTarget();

		// Target needs to NOT have ANY forging-enabled account flags set
		if (Forging.canForge(target))
			return ValidationResult.FORGING_ALREADY_ENABLED;

		// Has creator reached minimum requirements?

		// Already gifted maximum number of forging rights?
		int numberEnabledAccounts = this.repository.getAccountRepository().countForgingAccountsEnabledByAddress(creator.getAddress());
		if (numberEnabledAccounts >= forgingTier.maxSubAccounts)
			return ValidationResult.FORGING_ENABLE_LIMIT;

		// Not enough forged blocks to gift forging rights?
		int numberForgedBlocks = this.repository.getBlockRepository().countForgedBlocks(creator.getPublicKey());
		if (numberForgedBlocks < forgingTier.minBlocks)
			return ValidationResult.FORGE_MORE_BLOCKS;


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

		int forgeBit = creatorFlags & Forging.getForgingMask();
		// Target's forging bit is next level from creator's
		int targetForgeBit = forgeBit << 1;

		Account target = getTarget();
		Integer targetFlags = target.getFlags();
		if (targetFlags == null)
			targetFlags = 0;

		targetFlags |= targetForgeBit;
		
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

		int forgeBit = creatorFlags & Forging.getForgingMask();
		// Target's forging bit is next level from creator's
		int targetForgeBit = forgeBit << 1;

		Account target = getTarget();

		int targetFlags = target.getFlags();

		targetFlags &= ~targetForgeBit;

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
