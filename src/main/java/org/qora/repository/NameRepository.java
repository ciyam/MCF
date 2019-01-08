package org.qora.repository;

import java.util.List;

import org.qora.data.naming.NameData;

public interface NameRepository {

	public NameData fromName(String name) throws DataException;

	public boolean nameExists(String name) throws DataException;

	public List<NameData> getAllNames() throws DataException;

	public List<NameData> getNamesByOwner(String address) throws DataException;

	public void save(NameData nameData) throws DataException;

	public void delete(String name) throws DataException;

}
