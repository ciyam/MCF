package repository;

import data.transaction.TransactionData;
import data.block.BlockData;

public interface TransactionRepository {

	public TransactionData fromSignature(byte[] signature) throws DataException;

	public TransactionData fromReference(byte[] reference) throws DataException;

	public int getHeightFromSignature(byte[] signature) throws DataException;

	public BlockData getBlockDataFromSignature(byte[] signature) throws DataException;

	public void save(TransactionData transactionData) throws DataException;

	public void delete(TransactionData transactionData) throws DataException;

}
