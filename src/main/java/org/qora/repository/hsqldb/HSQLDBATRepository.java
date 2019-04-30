package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.qora.data.at.ATData;
import org.qora.data.at.ATStateData;
import org.qora.repository.ATRepository;
import org.qora.repository.DataException;

public class HSQLDBATRepository implements ATRepository {

	protected HSQLDBRepository repository;

	public HSQLDBATRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	// ATs

	@Override
	public ATData fromATAddress(String atAddress) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT creator, creation, version, asset_id, code_bytes, is_sleeping, sleep_until_height, is_finished, had_fatal_error, is_frozen, frozen_balance FROM ATs WHERE AT_address = ?",
				atAddress)) {
			if (resultSet == null)
				return null;

			byte[] creatorPublicKey = resultSet.getBytes(1);
			long creation = resultSet.getTimestamp(2, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			int version = resultSet.getInt(3);
			long assetId = resultSet.getLong(4);
			byte[] codeBytes = resultSet.getBytes(5); // Actually BLOB
			boolean isSleeping = resultSet.getBoolean(6);

			Integer sleepUntilHeight = resultSet.getInt(7);
			if (resultSet.wasNull())
				sleepUntilHeight = null;

			boolean isFinished = resultSet.getBoolean(8);
			boolean hadFatalError = resultSet.getBoolean(9);
			boolean isFrozen = resultSet.getBoolean(10);

			BigDecimal frozenBalance = resultSet.getBigDecimal(11);
			if (resultSet.wasNull())
				frozenBalance = null;

			return new ATData(atAddress, creatorPublicKey, creation, version, assetId, codeBytes, isSleeping, sleepUntilHeight, isFinished, hadFatalError,
					isFrozen, frozenBalance);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT from repository", e);
		}
	}

	@Override
	public List<ATData> getAllExecutableATs() throws DataException {
		List<ATData> executableATs = new ArrayList<ATData>();

		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT AT_address, creator, creation, version, asset_id, code_bytes, is_sleeping, sleep_until_height, had_fatal_error, is_frozen, frozen_balance FROM ATs WHERE is_finished = false ORDER BY creation ASC")) {
			if (resultSet == null)
				return executableATs;

			boolean isFinished = false;

			do {
				String atAddress = resultSet.getString(1);
				byte[] creatorPublicKey = resultSet.getBytes(2);
				long creation = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
				int version = resultSet.getInt(4);
				long assetId = resultSet.getLong(5);
				byte[] codeBytes = resultSet.getBytes(6); // Actually BLOB
				boolean isSleeping = resultSet.getBoolean(7);

				Integer sleepUntilHeight = resultSet.getInt(8);
				if (resultSet.wasNull())
					sleepUntilHeight = null;

				boolean hadFatalError = resultSet.getBoolean(9);
				boolean isFrozen = resultSet.getBoolean(10);

				BigDecimal frozenBalance = resultSet.getBigDecimal(11);
				if (resultSet.wasNull())
					frozenBalance = null;

				ATData atData = new ATData(atAddress, creatorPublicKey, creation, version, assetId, codeBytes, isSleeping, sleepUntilHeight, isFinished,
						hadFatalError, isFrozen, frozenBalance);

				executableATs.add(atData);
			} while (resultSet.next());

			return executableATs;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch executable ATs from repository", e);
		}
	}

	@Override
	public Integer getATCreationBlockHeight(String atAddress) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(
				"SELECT height from DeployATTransactions JOIN BlockTransactions ON transaction_signature = signature JOIN Blocks ON Blocks.signature = block_signature WHERE AT_address = ?",
				atAddress)) {
			if (resultSet == null)
				return null;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT's creation block height from repository", e);
		}
	}

	@Override
	public void save(ATData atData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("ATs");

		saveHelper.bind("AT_address", atData.getATAddress()).bind("creator", atData.getCreatorPublicKey()).bind("creation", new Timestamp(atData.getCreation()))
				.bind("version", atData.getVersion()).bind("asset_id", atData.getAssetId()).bind("code_bytes", atData.getCodeBytes())
				.bind("is_sleeping", atData.getIsSleeping()).bind("sleep_until_height", atData.getSleepUntilHeight())
				.bind("is_finished", atData.getIsFinished()).bind("had_fatal_error", atData.getHadFatalError()).bind("is_frozen", atData.getIsFrozen())
				.bind("frozen_balance", atData.getFrozenBalance());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save AT into repository", e);
		}
	}

	@Override
	public void delete(String atAddress) throws DataException {
		try {
			this.repository.delete("ATs", "AT_address = ?", atAddress);
			// AT States also deleted via ON DELETE CASCADE
		} catch (SQLException e) {
			throw new DataException("Unable to delete AT from repository", e);
		}
	}

	// AT State

	@Override
	public ATStateData getATStateAtHeight(String atAddress, int height) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT creation, state_data, state_hash, fees FROM ATStates WHERE AT_address = ? AND height = ?", atAddress, height)) {
			if (resultSet == null)
				return null;

			long creation = resultSet.getTimestamp(1, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			byte[] stateData = resultSet.getBytes(2); // Actually BLOB
			byte[] stateHash = resultSet.getBytes(3);
			BigDecimal fees = resultSet.getBigDecimal(4);

			return new ATStateData(atAddress, height, creation, stateData, stateHash, fees);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT state from repository", e);
		}
	}

	@Override
	public ATStateData getLatestATState(String atAddress) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT height, creation, state_data, state_hash, fees FROM ATStates WHERE AT_address = ? ORDER BY height DESC", atAddress)) {
			if (resultSet == null)
				return null;

			int height = resultSet.getInt(1);
			long creation = resultSet.getTimestamp(2, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			byte[] stateData = resultSet.getBytes(3); // Actually BLOB
			byte[] stateHash = resultSet.getBytes(4);
			BigDecimal fees = resultSet.getBigDecimal(5);

			return new ATStateData(atAddress, height, creation, stateData, stateHash, fees);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch latest AT state from repository", e);
		}
	}

	@Override
	public List<ATStateData> getBlockATStatesAtHeight(int height) throws DataException {
		List<ATStateData> atStates = new ArrayList<ATStateData>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT AT_address, state_hash, fees FROM ATStates WHERE height = ? ORDER BY creation ASC",
				height)) {
			if (resultSet == null)
				return atStates; // No atStates in this block

			// NB: do-while loop because .checkedExecute() implicitly calls ResultSet.next() for us
			do {
				String atAddress = resultSet.getString(1);
				byte[] stateHash = resultSet.getBytes(2);
				BigDecimal fees = resultSet.getBigDecimal(3);

				ATStateData atStateData = new ATStateData(atAddress, height, stateHash, fees);
				atStates.add(atStateData);
			} while (resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT states for this height from repository", e);
		}

		return atStates;
	}

	@Override
	public void save(ATStateData atStateData) throws DataException {
		// We shouldn't ever save partial ATStateData
		if (atStateData.getCreation() == null || atStateData.getStateHash() == null || atStateData.getHeight() == null)
			throw new IllegalArgumentException("Refusing to save partial AT state into repository!");

		HSQLDBSaver saveHelper = new HSQLDBSaver("ATStates");

		saveHelper.bind("AT_address", atStateData.getATAddress()).bind("height", atStateData.getHeight())
				.bind("creation", new Timestamp(atStateData.getCreation())).bind("state_data", atStateData.getStateData())
				.bind("state_hash", atStateData.getStateHash()).bind("fees", atStateData.getFees());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save AT state into repository", e);
		}
	}

	@Override
	public void delete(String atAddress, int height) throws DataException {
		try {
			this.repository.delete("ATStates", "AT_address = ? AND height = ?", atAddress, height);
		} catch (SQLException e) {
			throw new DataException("Unable to delete AT state from repository", e);
		}
	}

	@Override
	public void deleteATStates(int height) throws DataException {
		try {
			this.repository.delete("ATStates", "height = ?", height);
		} catch (SQLException e) {
			throw new DataException("Unable to delete AT states from repository", e);
		}
	}

}
