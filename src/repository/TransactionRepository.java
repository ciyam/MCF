package repository;

import data.transaction.TransactionData;
import data.block.BlockData;

public interface TransactionRepository {

	public TransactionData fromSignature(byte[] signature) throws DataException;

	public TransactionData fromReference(byte[] reference) throws DataException;

	public int getHeight(TransactionData transactionData);

	public BlockData toBlock(TransactionData transactionData);

	public void save(TransactionData transactionData) throws DataException;

	public void delete(TransactionData transactionData) throws DataException;

}
