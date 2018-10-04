package repository;

import data.at.ATData;
import data.at.ATStateData;

public interface ATRepository {

	// CIYAM AutomatedTransactions

	public ATData fromATAddress(String atAddress) throws DataException;

	public void save(ATData atData) throws DataException;

	public void delete(String atAddress) throws DataException;

	// AT States

	public ATStateData getATState(String atAddress, int height) throws DataException;

	public void save(ATStateData atStateData) throws DataException;

	public void delete(String atAddress, int height) throws DataException;

}
