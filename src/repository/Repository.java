package repository;

public abstract class Repository {

	protected TransactionRepository transactionRepository;
	protected BlockRepository blockRepository;

	public abstract void saveChanges() throws DataException ;
	public abstract void discardChanges() throws DataException ;
	public abstract void close() throws DataException ;
	
	public TransactionRepository getTransactionRepository() {
		return this.transactionRepository;
	}

	public BlockRepository getBlockRepository() {
		return this.blockRepository;
	}
}
