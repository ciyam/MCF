package repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import data.at.ATData;
import data.at.ATStateData;
import repository.ATRepository;
import repository.DataException;

public class HSQLDBATRepository implements ATRepository {

	protected HSQLDBRepository repository;

	public HSQLDBATRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	// ATs

	@Override
	public ATData fromATAddress(String atAddress) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT owner, asset_name, description, quantity, is_divisible, reference FROM Assets WHERE AT_address = ?", atAddress)) {
			if (resultSet == null)
				return null;

			int version = resultSet.getInt(1);
			byte[] codeBytes = resultSet.getBytes(2); // Actually BLOB
			boolean isSleeping = resultSet.getBoolean(3);

			Integer sleepUntilHeight = resultSet.getInt(4);
			if (resultSet.wasNull())
				sleepUntilHeight = null;

			boolean isFinished = resultSet.getBoolean(5);
			boolean hadFatalError = resultSet.getBoolean(6);
			boolean isFrozen = resultSet.getBoolean(7);

			BigDecimal frozenBalance = resultSet.getBigDecimal(8);
			if (resultSet.wasNull())
				frozenBalance = null;

			byte[] deploySignature = resultSet.getBytes(9);

			return new ATData(atAddress, version, codeBytes, isSleeping, sleepUntilHeight, isFinished, hadFatalError, isFrozen, frozenBalance, deploySignature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT from repository", e);
		}
	}

	@Override
	public void save(ATData atData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("ATs");

		saveHelper.bind("AT_address", atData.getATAddress()).bind("version", atData.getVersion()).bind("code_bytes", atData.getCodeBytes())
				.bind("is_sleeping", atData.getIsSleeping()).bind("sleep_until_height", atData.getSleepUntilHeight())
				.bind("is_finished", atData.getIsFinished()).bind("had_fatal_error", atData.getHadFatalError()).bind("is_frozen", atData.getIsFrozen())
				.bind("frozen_balance", atData.getFrozenBalance()).bind("deploy_signature", atData.getDeploySignature());

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
	public ATStateData getATState(String atAddress, int height) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT state_data FROM ATStates WHERE AT_address = ? AND height = ?", atAddress, height)) {
			if (resultSet == null)
				return null;

			byte[] stateData = resultSet.getBytes(1); // Actually BLOB

			return new ATStateData(atAddress, height, stateData);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT State from repository", e);
		}
	}

	@Override
	public void save(ATStateData atStateData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("ATStates");

		saveHelper.bind("AT_address", atStateData.getATAddress()).bind("height", atStateData.getHeight()).bind("state_data", atStateData.getStateData());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save AT State into repository", e);
		}
	}

	@Override
	public void delete(String atAddress, int height) throws DataException {
		try {
			this.repository.delete("ATStates", "AT_address = ? AND height = ?", atAddress, height);
		} catch (SQLException e) {
			throw new DataException("Unable to delete AT State from repository", e);
		}
	}

}
