package repository;

import data.transaction.Transaction;
import qora.block.Block;

public interface TransactionRepository {

	public Transaction fromSignature(byte[] signature);

	public Transaction fromReference(byte[] reference);

	public int getHeight(Transaction transaction);
	
	public Block toBlock(Transaction transaction);
	
	public void save(Transaction transaction);

	public void delete(Transaction transaction);

}
