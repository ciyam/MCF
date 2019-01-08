package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.qora.data.naming.NameData;
import org.qora.repository.DataException;
import org.qora.repository.NameRepository;

public class HSQLDBNameRepository implements NameRepository {

	protected HSQLDBRepository repository;

	public HSQLDBNameRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	@Override
	public NameData fromName(String name) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT registrant, owner, data, registered, updated, reference, is_for_sale, sale_price FROM Names WHERE name = ?", name)) {
			if (resultSet == null)
				return null;

			byte[] registrantPublicKey = resultSet.getBytes(1);
			String owner = resultSet.getString(2);
			String data = resultSet.getString(3);
			long registered = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

			// Special handling for possibly-NULL "updated" column
			Timestamp updatedTimestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC));
			Long updated = updatedTimestamp == null ? null : updatedTimestamp.getTime();

			byte[] reference = resultSet.getBytes(6);
			boolean isForSale = resultSet.getBoolean(7);
			BigDecimal salePrice = resultSet.getBigDecimal(8);

			return new NameData(registrantPublicKey, owner, name, data, registered, updated, reference, isForSale, salePrice);
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
	public List<NameData> getAllNames() throws DataException {
		List<NameData> names = new ArrayList<>();

		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT name, data, registrant, owner, registered, updated, reference, is_for_sale, sale_price FROM Names")) {
			if (resultSet == null)
				return names;

			do {
				String name = resultSet.getString(1);
				String data = resultSet.getString(2);
				byte[] registrantPublicKey = resultSet.getBytes(3);
				String owner = resultSet.getString(4);
				long registered = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				// Special handling for possibly-NULL "updated" column
				Timestamp updatedTimestamp = resultSet.getTimestamp(6, Calendar.getInstance(HSQLDBRepository.UTC));
				Long updated = updatedTimestamp == null ? null : updatedTimestamp.getTime();

				byte[] reference = resultSet.getBytes(7);
				boolean isForSale = resultSet.getBoolean(8);
				BigDecimal salePrice = resultSet.getBigDecimal(9);

				names.add(new NameData(registrantPublicKey, owner, name, data, registered, updated, reference, isForSale, salePrice));
			} while (resultSet.next());

			return names;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch names from repository", e);
		}
	}

	@Override
	public List<NameData> getNamesByOwner(String owner) throws DataException {
		List<NameData> names = new ArrayList<>();

		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT name, data, registrant, registered, updated, reference, is_for_sale, sale_price FROM Names WHERE owner = ?", owner)) {
			if (resultSet == null)
				return names;

			do {
				String name = resultSet.getString(1);
				String data = resultSet.getString(2);
				byte[] registrantPublicKey = resultSet.getBytes(3);
				long registered = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				// Special handling for possibly-NULL "updated" column
				Timestamp updatedTimestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC));
				Long updated = updatedTimestamp == null ? null : updatedTimestamp.getTime();

				byte[] reference = resultSet.getBytes(6);
				boolean isForSale = resultSet.getBoolean(7);
				BigDecimal salePrice = resultSet.getBigDecimal(8);

				names.add(new NameData(registrantPublicKey, owner, name, data, registered, updated, reference, isForSale, salePrice));
			} while (resultSet.next());

			return names;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's names from repository", e);
		}
	}

	@Override
	public void save(NameData nameData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Names");

		// Special handling for "updated" timestamp
		Long updated = nameData.getUpdated();
		Timestamp updatedTimestamp = updated == null ? null : new Timestamp(updated);

		saveHelper.bind("registrant", nameData.getRegistrantPublicKey()).bind("owner", nameData.getOwner()).bind("name", nameData.getName())
				.bind("data", nameData.getData()).bind("registered", new Timestamp(nameData.getRegistered())).bind("updated", updatedTimestamp)
				.bind("reference", nameData.getReference()).bind("is_for_sale", nameData.getIsForSale()).bind("sale_price", nameData.getSalePrice());

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
