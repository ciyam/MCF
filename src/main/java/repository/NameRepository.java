package repository;

import data.naming.NameData;

public interface NameRepository {

	public NameData fromName(String name) throws DataException;

	public boolean nameExists(String name) throws DataException;

	public void save(NameData nameData) throws DataException;

	public void delete(String name) throws DataException;

}
