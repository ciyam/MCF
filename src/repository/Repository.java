package repository;

public abstract class Repository {

	protected TransactionRepository transactionRepository;
	protected BlockRepository blockRepository;

	public TransactionRepository getTransactionRepository() {
		return this.transactionRepository;
	}

	public BlockRepository getBlockRepository() {
		return this.blockRepository;
	}
}
