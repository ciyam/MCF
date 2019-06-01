package org.qora.repository;

import java.util.List;

import org.qora.data.at.ATData;
import org.qora.data.at.ATStateData;

public interface ATRepository {

	// CIYAM AutomatedTransactions

	/** Returns ATData using AT's address or null if none found */
	public ATData fromATAddress(String atAddress) throws DataException;

	/** Returns where AT with passed address exists in repository */
	public boolean exists(String atAddress) throws DataException;

	/** Returns list of executable ATs, empty if none found */
	public List<ATData> getAllExecutableATs() throws DataException;

	/** Returns creation block height given AT's address or null if not found */
	public Integer getATCreationBlockHeight(String atAddress) throws DataException;

	/** Saves ATData into repository */
	public void save(ATData atData) throws DataException;

	/** Removes an AT from repository, including associated ATStateData */
	public void delete(String atAddress) throws DataException;

	// AT States

	/**
	 * Returns ATStateData for an AT at given height.
	 * 
	 * @param atAddress
	 *            - AT's address
	 * @param height
	 *            - block height
	 * @return ATStateData for AT at given height or null if none found
	 */
	public ATStateData getATStateAtHeight(String atAddress, int height) throws DataException;

	/**
	 * Returns latest ATStateData for an AT.
	 * <p>
	 * As ATs don't necessarily run every block, this will return the <tt>ATStateData</tt> with the greatest height.
	 * 
	 * @param atAddress
	 *            - AT's address
	 * @return ATStateData for AT with greatest height or null if none found
	 */
	public ATStateData getLatestATState(String atAddress) throws DataException;

	/**
	 * Returns all ATStateData for a given block height.
	 * <p>
	 * Unlike <tt>getATState</tt>, only returns ATStateData saved at the given height.
	 *
	 * @param height
	 *            - block height
	 * @return list of ATStateData for given height, empty list if none found
	 * @throws DataException
	 */
	public List<ATStateData> getBlockATStatesAtHeight(int height) throws DataException;

	/**
	 * Save ATStateData into repository.
	 * <p>
	 * Note: Requires at least these <tt>ATStateData</tt> properties to be filled, or an <tt>IllegalArgumentException</tt> will be thrown:
	 * <p>
	 * <ul>
	 * <li><tt>creation</tt></li>
	 * <li><tt>stateHash</tt></li>
	 * <li><tt>height</tt></li>
	 * </ul>
	 * 
	 * @param atStateData
	 * @throws IllegalArgumentException
	 */
	public void save(ATStateData atStateData) throws DataException;

	/** Delete AT's state data at this height */
	public void delete(String atAddress, int height) throws DataException;

	/** Delete state data for all ATs at this height */
	public void deleteATStates(int height) throws DataException;

}
