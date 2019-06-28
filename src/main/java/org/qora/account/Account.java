package org.qora.account;

import java.math.BigDecimal;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.asset.Asset;
import org.qora.block.Block;
import org.qora.block.BlockChain;
import org.qora.data.account.AccountBalanceData;
import org.qora.data.account.AccountData;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.BlockRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.transaction.Transaction;

public class Account {

	private static final Logger LOGGER = LogManager.getLogger(Account.class);

	public static final int TIER1_FORGING_MASK = 0x1;
	public static final int TIER2_FORGING_MASK = 0x2;
	public static final int TIER3_FORGING_MASK = 0x4;
	public static final int FORGING_MASK = TIER1_FORGING_MASK | TIER2_FORGING_MASK | TIER3_FORGING_MASK;

	public static final int ADDRESS_LENGTH = 25;

	protected Repository repository;
	protected String address;

	protected Account() {
	}

	/** Construct Account business object using account's address */
	public Account(Repository repository, String address) {
		this.repository = repository;
		this.address = address;
	}

	// Simple getters / setters

	public String getAddress() {
		return this.address;
	}

	/**
	 * Build AccountData object using available account information.
	 * <p>
	 * For example, PublicKeyAccount might override and add public key info.
	 * 
	 * @return
	 */
	protected AccountData buildAccountData() {
		return new AccountData(this.address);
	}

	// More information

	/**
	 * Calculate current generating balance for this account.
	 * <p>
	 * This is the current confirmed balance minus amounts received in the last <code>BlockChain.BLOCK_RETARGET_INTERVAL</code> blocks.
	 * 
	 * @throws DataException
	 */
	public BigDecimal getGeneratingBalance() throws DataException {
		BigDecimal balance = this.getConfirmedBalance(Asset.QORA);

		BlockRepository blockRepository = this.repository.getBlockRepository();
		BlockData blockData = blockRepository.getLastBlock();

		for (int i = 1; i < BlockChain.getInstance().getBlockDifficultyInterval() && blockData != null && blockData.getHeight() > 1; ++i) {
			Block block = new Block(this.repository, blockData);

			// CIYAM AT transactions should be fetched from repository so no special handling needed here
			for (Transaction transaction : block.getTransactions()) {
				if (transaction.isInvolved(this)) {
					final BigDecimal amount = transaction.getAmount(this);

					// Subtract positive amounts only
					if (amount.compareTo(BigDecimal.ZERO) > 0)
						balance = balance.subtract(amount);
				}
			}

			blockData = block.getParent();
		}

		// Do not go below 0
		balance = balance.max(BigDecimal.ZERO);

		return balance;
	}

	// Balance manipulations - assetId is 0 for QORA

	public BigDecimal getBalance(long assetId, int confirmations) throws DataException {
		// Simple case: we only need balance with 1 confirmation
		if (confirmations == 1)
			return this.getConfirmedBalance(assetId);

		/*
		 * For a balance with more confirmations work back from last block, undoing transactions involving this account, until we have processed required number
		 * of blocks.
		 */
		BlockRepository blockRepository = this.repository.getBlockRepository();
		BigDecimal balance = this.getConfirmedBalance(assetId);
		BlockData blockData = blockRepository.getLastBlock();

		// Note: "blockData.getHeight() > 1" to make sure we don't examine genesis block
		for (int i = 1; i < confirmations && blockData != null && blockData.getHeight() > 1; ++i) {
			Block block = new Block(this.repository, blockData);

			// CIYAM AT transactions should be fetched from repository so no special handling needed here
			for (Transaction transaction : block.getTransactions())
				if (transaction.isInvolved(this))
					balance = balance.subtract(transaction.getAmount(this));

			blockData = block.getParent();
		}

		// Return balance
		return balance;
	}

	public BigDecimal getConfirmedBalance(long assetId) throws DataException {
		AccountBalanceData accountBalanceData = this.repository.getAccountRepository().getBalance(this.address, assetId);
		if (accountBalanceData == null)
			return BigDecimal.ZERO.setScale(8);

		return accountBalanceData.getBalance();
	}

	public void setConfirmedBalance(long assetId, BigDecimal balance) throws DataException {
		// Safety feature!
		if (balance.compareTo(BigDecimal.ZERO) < 0) {
			String message = String.format("Refusing to set negative balance %s [assetId %d] for %s", balance.toPlainString(), assetId, this.address);
			LOGGER.error(message);
			throw new DataException(message);
		}

		// Can't have a balance without an account - make sure it exists!
		this.repository.getAccountRepository().ensureAccount(this.buildAccountData());

		AccountBalanceData accountBalanceData = new AccountBalanceData(this.address, assetId, balance);
		this.repository.getAccountRepository().save(accountBalanceData);

		LOGGER.trace(this.address + " balance now: " + balance.toPlainString() + " [assetId " + assetId + "]");
	}

	public void deleteBalance(long assetId) throws DataException {
		this.repository.getAccountRepository().delete(this.address, assetId);
	}

	// Reference manipulations

	/**
	 * Fetch last reference for account.
	 * 
	 * @return byte[] reference, or null if no reference or account not found.
	 * @throws DataException
	 */
	public byte[] getLastReference() throws DataException {
		return this.repository.getAccountRepository().getLastReference(this.address);
	}

	/**
	 * Fetch last reference for account, considering unconfirmed transactions.
	 * <p>
	 * NOTE: a repository savepoint may be used during execution.
	 * 
	 * @return byte[] reference, or null if no reference or account not found.
	 * @throws DataException
	 */
	public byte[] getUnconfirmedLastReference() throws DataException {
		// Newest unconfirmed transaction takes priority
		List<TransactionData> unconfirmedTransactions = Transaction.getUnconfirmedTransactions(repository);

		byte[] reference = null;

		for (TransactionData transactionData : unconfirmedTransactions) {
			String address = PublicKeyAccount.getAddress(transactionData.getCreatorPublicKey());

			if (address.equals(this.address))
				reference = transactionData.getSignature();
		}

		if (reference != null)
			return reference;

		// No unconfirmed transactions
		return getLastReference();
	}

	/**
	 * Set last reference for account.
	 * 
	 * @param reference
	 *            -- null allowed
	 * @throws DataException
	 */
	public void setLastReference(byte[] reference) throws DataException {
		AccountData accountData = this.buildAccountData();
		accountData.setReference(reference);
		this.repository.getAccountRepository().setLastReference(accountData);
	}

	// Default groupID manipulations

	/** Returns account's default groupID or null if account doesn't exist. */
	public Integer getDefaultGroupId() throws DataException {
		return this.repository.getAccountRepository().getDefaultGroupId(this.address);
	}

	/**
	 * Sets account's default groupID and saves into repository.
	 * <p>
	 * Caller will need to call <tt>repository.saveChanges()</tt>.
	 * 
	 * @param defaultGroupId
	 * @throws DataException
	 */
	public void setDefaultGroupId(int defaultGroupId) throws DataException {
		AccountData accountData = this.buildAccountData();
		accountData.setDefaultGroupId(defaultGroupId);
		this.repository.getAccountRepository().setDefaultGroupId(accountData);

		LOGGER.trace(String.format("Account %s defaultGroupId now %d", accountData.getAddress(), defaultGroupId));
	}

	// Account flags

	public Integer getFlags() throws DataException {
		return this.repository.getAccountRepository().getFlags(this.address);
	}

	public void setFlags(int flags) throws DataException {
		AccountData accountData = this.buildAccountData();
		accountData.setFlags(flags);
		this.repository.getAccountRepository().setFlags(accountData);
	}

	// Forging Enabler

	public void setForgingEnabler(String address) throws DataException {
		AccountData accountData = this.buildAccountData();
		accountData.setForgingEnabler(address);
		this.repository.getAccountRepository().setForgingEnabler(accountData);
	}

}
