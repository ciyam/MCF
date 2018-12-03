package repository;

import data.transaction.TransactionData;

import java.util.List;

import data.block.BlockData;

public interface TransactionRepository {

	public TransactionData fromSignature(byte[] signature) throws DataException;

	public TransactionData fromReference(byte[] reference) throws DataException;

	public TransactionData fromHeightAndSequence(int height, int sequence) throws DataException;

	/** Returns block height containing transaction or 0 if not in a block or transaction doesn't exist */
	public int getHeightFromSignature(byte[] signature) throws DataException;

	@Deprecated
	public BlockData getBlockDataFromSignature(byte[] signature) throws DataException;

	/**
	 * Returns list of unconfirmed transactions in timestamp-else-signature order.
	 * 
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
	public List<TransactionData> getAllUnconfirmedTransactions() throws DataException;

	/**
	 * Remove transaction from unconfirmed transactions pile.
	 * 
	 * @param signature
	 * @throws DataException
	 */
	public void confirmTransaction(byte[] signature) throws DataException;

	public void save(TransactionData transactionData) throws DataException;

	public void delete(TransactionData transactionData) throws DataException;

}
