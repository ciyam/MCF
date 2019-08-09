package org.qora.repository;

public interface RepositoryFactory {

	public Repository getRepository() throws DataException;

	public Repository tryRepository() throws DataException;

	public void close() throws DataException;

}
