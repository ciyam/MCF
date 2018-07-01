package repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;

import data.naming.NameData;
import repository.NameRepository;
import repository.DataException;

public class HSQLDBNameRepository implements NameRepository {

	protected HSQLDBRepository repository;

	public HSQLDBNameRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	@Override
	public NameData fromName(String name) throws DataException {
		try {
			ResultSet resultSet = this.repository.checkedExecute("SELECT registrant, owner, name, registered FROM Names WHERE name = ?", name);
			if (resultSet == null)
				return null;

			byte[] registrantPublicKey = resultSet.getBytes(1);
			String owner = resultSet.getString(2);
			String data = resultSet.getString(3);
			long timestamp = resultSet.getLong(4);

			return new NameData(registrantPublicKey, owner, name, data, timestamp);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch name info from repository", e);
		}
	}

	@Override
	public boolean nameExists(String name) throws DataException {
		try {
			return this.repository.exists("Names", "name = ?", name);
		} catch (SQLException e) {
			throw new DataException("Unable to check for name in repository", e);
		}
	}

	@Override
	public void save(NameData nameData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Names");

		saveHelper.bind("registrant", nameData.getRegistrantPublicKey()).bind("owner", nameData.getOwner()).bind("name", nameData.getName())
				.bind("data", nameData.getData()).bind("registered", nameData.getRegistered());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save name info into repository", e);
		}
	}

	@Override
	public void delete(String name) throws DataException {
		try {
			this.repository.delete("Names", "name = ?", name);
		} catch (SQLException e) {
			throw new DataException("Unable to delete name info from repository", e);
		}
	}

}
