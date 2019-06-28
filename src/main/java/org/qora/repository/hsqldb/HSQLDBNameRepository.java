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
				.checkedExecute("SELECT owner, data, registered, updated, reference, is_for_sale, sale_price, creation_group_id FROM Names WHERE name = ?", name)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String data = resultSet.getString(2);
			long registered = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

			// Special handling for possibly-NULL "updated" column
			Timestamp updatedTimestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC));
			Long updated = updatedTimestamp == null ? null : updatedTimestamp.getTime();

			byte[] reference = resultSet.getBytes(5);
			boolean isForSale = resultSet.getBoolean(6);
			BigDecimal salePrice = resultSet.getBigDecimal(7);
			int creationGroupId = resultSet.getInt(8);

			return new NameData(owner, name, data, registered, updated, reference, isForSale, salePrice, creationGroupId);
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
	public List<NameData> getAllNames(Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT name, data, owner, registered, updated, reference, is_for_sale, sale_price, creation_group_id FROM Names ORDER BY name";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<NameData> names = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return names;

			do {
				String name = resultSet.getString(1);
				String data = resultSet.getString(2);
				String owner = resultSet.getString(3);
				long registered = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				// Special handling for possibly-NULL "updated" column
				Timestamp updatedTimestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC));
				Long updated = updatedTimestamp == null ? null : updatedTimestamp.getTime();

				byte[] reference = resultSet.getBytes(6);
				boolean isForSale = resultSet.getBoolean(7);
				BigDecimal salePrice = resultSet.getBigDecimal(8);
				int creationGroupId = resultSet.getInt(9);

				names.add(new NameData(owner, name, data, registered, updated, reference, isForSale, salePrice, creationGroupId));
			} while (resultSet.next());

			return names;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch names from repository", e);
		}
	}

	@Override
	public List<NameData> getNamesForSale(Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT name, data, owner, registered, updated, reference, sale_price, creation_group_id FROM Names WHERE is_for_sale = TRUE ORDER BY name";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<NameData> names = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return names;

			do {
				String name = resultSet.getString(1);
				String data = resultSet.getString(2);
				String owner = resultSet.getString(3);
				long registered = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				// Special handling for possibly-NULL "updated" column
				Timestamp updatedTimestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC));
				Long updated = updatedTimestamp == null ? null : updatedTimestamp.getTime();

				byte[] reference = resultSet.getBytes(6);
				boolean isForSale = true;
				BigDecimal salePrice = resultSet.getBigDecimal(7);
				int creationGroupId = resultSet.getInt(8);

				names.add(new NameData(owner, name, data, registered, updated, reference, isForSale, salePrice, creationGroupId));
			} while (resultSet.next());

			return names;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch names from repository", e);
		}
	}

	@Override
	public List<NameData> getNamesByOwner(String owner, Integer limit, Integer offset, Boolean reverse) throws DataException {
		String sql = "SELECT name, data, registered, updated, reference, is_for_sale, sale_price, creation_group_id FROM Names WHERE owner = ? ORDER BY name";
		if (reverse != null && reverse)
			sql += " DESC";
		sql += HSQLDBRepository.limitOffsetSql(limit, offset);

		List<NameData> names = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, owner)) {
			if (resultSet == null)
				return names;

			do {
				String name = resultSet.getString(1);
				String data = resultSet.getString(2);
				long registered = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				// Special handling for possibly-NULL "updated" column
				Timestamp updatedTimestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC));
				Long updated = updatedTimestamp == null ? null : updatedTimestamp.getTime();

				byte[] reference = resultSet.getBytes(5);
				boolean isForSale = resultSet.getBoolean(6);
				BigDecimal salePrice = resultSet.getBigDecimal(7);
				int creationGroupId = resultSet.getInt(8);

				names.add(new NameData(owner, name, data, registered, updated, reference, isForSale, salePrice, creationGroupId));
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

		saveHelper.bind("owner", nameData.getOwner()).bind("name", nameData.getName()).bind("data", nameData.getData())
				.bind("registered", new Timestamp(nameData.getRegistered())).bind("updated", updatedTimestamp).bind("reference", nameData.getReference())
				.bind("is_for_sale", nameData.getIsForSale()).bind("sale_price", nameData.getSalePrice())
				.bind("creation_group_id", nameData.getCreationGroupId());

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
