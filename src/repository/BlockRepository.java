package repository;

import java.util.List;

import data.block.BlockData;
import data.block.BlockTransactionData;
import data.transaction.TransactionData;

public interface BlockRepository {

	public BlockData fromSignature(byte[] signature) throws DataException;

	public BlockData fromReference(byte[] reference) throws DataException;

	public BlockData fromHeight(int height) throws DataException;

	/**
	 * Return height of block in blockchain using block's signature.
	 * 
	 * @param signature
	 * @return height, or 0 if not found in blockchain.
	 * @throws DataException
	 */
	public int getHeightFromSignature(byte[] signature) throws DataException;

	/**
	 * Return highest block height from repository.
	 * 
	 * @return height, or 0 if there are no blocks in DB (not very likely).
	 */
	public int getBlockchainHeight() throws DataException;

	/**
	 * Return highest block in blockchain.
	 * 
	 * @return highest block's data
	 * @throws DataException
	 */
	public BlockData getLastBlock() throws DataException;

	public List<TransactionData> getTransactionsFromSignature(byte[] signature) throws DataException;

	public void save(BlockData blockData) throws DataException;

	public void delete(BlockData blockData) throws DataException;

	public void save(BlockTransactionData blockTransactionData) throws DataException;

	public void delete(BlockTransactionData blockTransactionData) throws DataException;

}
