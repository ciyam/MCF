package repository;

import data.transaction.TransactionData;
import qora.transaction.Transaction.TransactionType;

import java.util.List;

public interface TransactionRepository {

	// Fetching transactions / transaction height

	public TransactionData fromSignature(byte[] signature) throws DataException;

	public TransactionData fromReference(byte[] reference) throws DataException;

	public TransactionData fromHeightAndSequence(int height, int sequence) throws DataException;

	/** Returns block height containing transaction or 0 if not in a block or transaction doesn't exist */
	public int getHeightFromSignature(byte[] signature) throws DataException;

	// Transaction participants

	public List<byte[]> getAllSignaturesInvolvingAddress(String address) throws DataException;

	public void saveParticipants(TransactionData transactionData, List<String> participants) throws DataException;

	public void deleteParticipants(TransactionData transactionData) throws DataException;

	// Searching transactions

	public List<byte[]> getAllSignaturesMatchingCriteria(Integer startBlock, Integer blockLimit, TransactionType txType, String address) throws DataException;

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
