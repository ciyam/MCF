package qora.account;

import java.math.BigDecimal;

import data.account.AccountBalanceData;
import data.account.AccountData;
import data.block.BlockData;
import qora.assets.Asset;
import qora.block.Block;
import qora.block.BlockChain;
import qora.transaction.Transaction;
import repository.BlockRepository;
import repository.DataException;
import repository.Repository;

public class Account {

	public static final int ADDRESS_LENGTH = 25;

	protected Repository repository;
	protected AccountData accountData;

	protected Account() {
	}

	public Account(Repository repository, String address) throws DataException {
		this.repository = repository;
		this.accountData = new AccountData(address);
	}

	public String getAddress() {
		return this.accountData.getAddress();
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

		for (int i = 1; i < BlockChain.BLOCK_RETARGET_INTERVAL && blockData != null && blockData.getHeight() > 1; ++i) {
			Block block = new Block(this.repository, blockData);

			for (Transaction transaction : block.getTransactions()) {
				if (transaction.isInvolved(this)) {
					final BigDecimal amount = transaction.getAmount(this);

					// Subtract positive amounts only
					if (amount.compareTo(BigDecimal.ZERO) > 0)
						balance = balance.subtract(amount);
				}
			}

			// TODO - CIYAM AT support needed
			/*
			 * LinkedHashMap<Tuple2<Integer, Integer>, AT_Transaction> atTxs = db.getATTransactionMap().getATTransactions(block.getHeight(db));
			 * Iterator<AT_Transaction> iter = atTxs.values().iterator(); while (iter.hasNext()) { AT_Transaction key = iter.next();
			 * 
			 * if (key.getRecipient().equals(this.getAddress())) balance = balance.subtract(BigDecimal.valueOf(key.getAmount(), 8)); }
			 */

			blockData = block.getParent();
		}

		// Do not go below 0
		// XXX: How would this even be possible?
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

			for (Transaction transaction : block.getTransactions())
				if (transaction.isInvolved(this))
					balance = balance.subtract(transaction.getAmount(this));

			// TODO - CIYAM AT support
			/*
			 * // Also check AT transactions for amounts received to this account LinkedHashMap<Tuple2<Integer, Integer>, AT_Transaction> atTxs =
			 * db.getATTransactionMap().getATTransactions(block.getHeight(db)); Iterator<AT_Transaction> iter = atTxs.values().iterator(); while
			 * (iter.hasNext()) { AT_Transaction key = iter.next();
			 * 
			 * if (key.getRecipient().equals(this.getAddress())) balance = balance.subtract(BigDecimal.valueOf(key.getAmount(), 8)); }
			 */

			blockData = block.getParent();
		}

		// Return balance
		return balance;
	}

	public BigDecimal getConfirmedBalance(long assetId) throws DataException {
		AccountBalanceData accountBalanceData = this.repository.getAccountRepository().getBalance(this.accountData.getAddress(), assetId);
		if (accountBalanceData == null)
			return BigDecimal.ZERO.setScale(8);

		return accountBalanceData.getBalance();
	}

	public void setConfirmedBalance(long assetId, BigDecimal balance) throws DataException {
		AccountBalanceData accountBalanceData = new AccountBalanceData(this.accountData.getAddress(), assetId, balance);
		this.repository.getAccountRepository().save(accountBalanceData);
	}

	public void deleteBalance(long assetId) throws DataException {
		this.repository.getAccountRepository().delete(this.accountData.getAddress(), assetId);
	}

	// Reference manipulations

	/**
	 * Fetch last reference for account.
	 * 
	 * @return byte[] reference, or null if no reference or account not found.
	 * @throws DataException
	 */
	public byte[] getLastReference() throws DataException {
		AccountData accountData = this.repository.getAccountRepository().getAccount(this.accountData.getAddress());
		if (accountData == null)
			return null;

		return accountData.getReference();
	}

	/**
	 * Set last reference for account.
	 * 
	 * @param reference
	 *            -- null allowed
	 * @throws DataException
	 */
	public void setLastReference(byte[] reference) throws DataException {
		this.repository.getAccountRepository().save(accountData);
	}

}
