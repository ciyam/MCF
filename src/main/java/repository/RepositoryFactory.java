package repository;

public interface RepositoryFactory {

	public Repository getRepository() throws DataException;

	public void close() throws DataException;

}
