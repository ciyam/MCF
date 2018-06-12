package repository;

public interface Repository {

	public AccountRepository getAccountRepository();

	public BlockRepository getBlockRepository();

	public TransactionRepository getTransactionRepository();

	public void saveChanges() throws DataException;

	public void discardChanges() throws DataException;

	public void close() throws DataException;

}
