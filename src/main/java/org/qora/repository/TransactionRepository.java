package org.qora.repository;

import java.util.List;

import org.qora.api.resource.TransactionsResource.ConfirmationStatus;
import org.qora.data.transaction.GroupApprovalTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;

public interface TransactionRepository {

	// Fetching transactions / transaction height

	public TransactionData fromSignature(byte[] signature) throws DataException;

	public TransactionData fromReference(byte[] reference) throws DataException;

	public TransactionData fromHeightAndSequence(int height, int sequence) throws DataException;

	/** Returns block height containing transaction or 0 if not in a block or transaction doesn't exist */
	public int getHeightFromSignature(byte[] signature) throws DataException;

	public boolean exists(byte[] signature) throws DataException;

	// Transaction participants

	public List<byte[]> getSignaturesInvolvingAddress(String address) throws DataException;

	public void saveParticipants(TransactionData transactionData, List<String> participants) throws DataException;

	public void deleteParticipants(TransactionData transactionData) throws DataException;

	// Searching transactions

	public List<byte[]> getSignaturesMatchingCriteria(Integer startBlock, Integer blockLimit, TransactionType txType, String address,
			ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns list of transactions relating to specific asset ID.
	 * 
	 * @param assetId
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return list of transactions, or empty if none
	 */
	public List<TransactionData> getAssetTransactions(int assetId, ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse)
			throws DataException;

	/**
	 * Returns list of transactions pending approval, with optional txGgroupId filtering.
	 * <p>
	 * This is typically called by the API.
	 * 
	 * @param txGroupId
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
	public List<TransactionData> getPendingTransactions(Integer txGroupId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/** Returns number of approvals for transaction with given signature. */
	public int countTransactionApprovals(int txGroupId, byte[] signature) throws DataException;

	/**
	 * Returns list of latest approval decisions per admin for given pending transaction signature.
	 * 
	 * @param signature
	 * @param adminPublicKey
	 *            restrict results to decision by this admin, pass null for all admins' results
	 * @return
	 * @throws DataException
	 */
	public List<GroupApprovalTransactionData> getLatestApprovals(byte[] pendingSignature, byte[] adminPublicKey) throws DataException;

	/**
	 * Returns whether transaction is confirmed or not.
	 * 
	 * @param signature
	 * @return true if confirmed, false if not.
	 */
	public boolean isConfirmed(byte[] signature) throws DataException;

	/**
	 * Returns list of unconfirmed transactions in timestamp-else-signature order.
	 * <p>
	 * This is typically called by the API.
	 * 
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
	public List<TransactionData> getUnconfirmedTransactions(Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns list of unconfirmed transactions in timestamp-else-signature order.
	 * 
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
	public default List<TransactionData> getUnconfirmedTransactions() throws DataException {
		return getUnconfirmedTransactions(null, null, null);
	}

	/**
	 * Remove transaction from unconfirmed transactions pile.
	 * 
	 * @param signature
	 * @throws DataException
	 */
	public void confirmTransaction(byte[] signature) throws DataException;

	/**
	 * Add transaction to unconfirmed transactions pile.
	 * 
	 * @param transactionData
	 * @throws DataException
	 */
	public void unconfirmTransaction(TransactionData transactionData) throws DataException;

	public void save(TransactionData transactionData) throws DataException;

	public void delete(TransactionData transactionData) throws DataException;

}
