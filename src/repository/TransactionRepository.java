package repository;

import data.transaction.TransactionData;
import data.block.BlockData;

public interface TransactionRepository {

	public TransactionData fromSignature(byte[] signature);

	public TransactionData fromReference(byte[] reference);

	public int getHeight(TransactionData transaction);
	
	public BlockData toBlock(TransactionData transaction);
	
	public void save(TransactionData transaction) throws DataException;

	public void delete(TransactionData transaction) throws DataException;

}
