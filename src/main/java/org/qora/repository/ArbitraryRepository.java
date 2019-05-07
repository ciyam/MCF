package org.qora.repository;

import org.qora.data.transaction.ArbitraryTransactionData;

public interface ArbitraryRepository {

	public boolean isDataLocal(byte[] signature) throws DataException;

	public byte[] fetchData(byte[] signature) throws DataException;

	public void save(ArbitraryTransactionData arbitraryTransactionData) throws DataException;

	public void delete(ArbitraryTransactionData arbitraryTransactionData) throws DataException;

}
