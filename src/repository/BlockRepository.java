package repository;

import java.util.List;

import data.block.BlockData;
import data.transaction.TransactionData;

public interface BlockRepository {

	public BlockData fromSignature(byte[] signature) throws DataException;

	public BlockData fromReference(byte[] reference) throws DataException;

	public BlockData fromHeight(int height) throws DataException;

	public List<TransactionData> getTransactionsFromSignature(byte[] signature) throws DataException;

	public void save(BlockData blockData) throws DataException;

}
