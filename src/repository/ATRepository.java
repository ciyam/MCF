package repository;

import java.util.List;

import data.at.ATData;
import data.at.ATStateData;

public interface ATRepository {

	// CIYAM AutomatedTransactions

	public ATData fromATAddress(String atAddress) throws DataException;

	public void save(ATData atData) throws DataException;

	public void delete(String atAddress) throws DataException;

	// AT States

	public ATStateData getATState(String atAddress, int height) throws DataException;

	public List<ATStateData> getBlockATStatesFromHeight(int height) throws DataException;

	public void save(ATStateData atStateData) throws DataException;

	/** Delete AT's state data at this height */
	public void delete(String atAddress, int height) throws DataException;

	/** Delete state data for all ATs at this height */
	public void deleteATStates(int height) throws DataException;

}
