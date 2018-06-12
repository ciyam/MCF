package repository;

public interface Repository {

	public BlockRepository getBlockRepository();

	public BlockTransactionRepository getBlockTransactionRepository();

	public TransactionRepository getTransactionRepository();

	public void saveChanges() throws DataException;

	public void discardChanges() throws DataException;

	public void close() throws DataException;

}
