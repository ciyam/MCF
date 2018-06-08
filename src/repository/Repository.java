package repository;

public abstract class Repository {

	protected TransactionRepository transactionRepository;

	public TransactionRepository getTransactionRepository() {
		return this.transactionRepository;
	}

}
