package repository;

import data.transaction.Transaction;

public interface TransactionRepository extends Repository {

	public Transaction fromSignature(byte[] signature);

}
