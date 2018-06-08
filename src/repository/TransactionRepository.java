package repository;

import data.transaction.Transaction;

public interface TransactionRepository {

	public Transaction fromSignature(byte[] signature);

	public Transaction fromReference(byte[] reference);

}
