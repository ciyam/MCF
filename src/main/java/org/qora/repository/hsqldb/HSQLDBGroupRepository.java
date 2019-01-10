package org.qora.repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.qora.data.group.GroupData;
import org.qora.repository.DataException;
import org.qora.repository.GroupRepository;

public class HSQLDBGroupRepository implements GroupRepository {

	protected HSQLDBRepository repository;

	public HSQLDBGroupRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	@Override
	public GroupData fromGroupName(String groupName) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT owner, description, created, updated, reference, is_open FROM AccountGroups WHERE group_name = ?", groupName)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String description = resultSet.getString(2);
			long created = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

			// Special handling for possibly-NULL "updated" column
			Timestamp updatedTimestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC));
			Long updated = resultSet.wasNull() ? null : updatedTimestamp.getTime();

			byte[] reference = resultSet.getBytes(5);
			boolean isOpen = resultSet.getBoolean(6);

			return new GroupData(owner, groupName, description, created, updated, isOpen, reference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group info from repository", e);
		}
	}

	@Override
	public boolean groupExists(String groupName) throws DataException {
		try {
			return this.repository.exists("AccountGroups", "group_name = ?", groupName);
		} catch (SQLException e) {
			throw new DataException("Unable to check for group in repository", e);
		}
	}

	@Override
	public List<GroupData> getAllGroups() throws DataException {
		List<GroupData> groups = new ArrayList<>();

		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_name, description, owner, created, updated, reference, is_open FROM AccountGroups")) {
			if (resultSet == null)
				return groups;

			do {
				String groupName = resultSet.getString(1);
				String description = resultSet.getString(2);
				String owner = resultSet.getString(3);
				long created = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				// Special handling for possibly-NULL "updated" column
				Timestamp updatedTimestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC));
				Long updated = resultSet.wasNull() ? null : updatedTimestamp.getTime();

				byte[] reference = resultSet.getBytes(6);
				boolean isOpen = resultSet.getBoolean(7);

				groups.add(new GroupData(owner, groupName, description, created, updated, isOpen, reference));
			} while (resultSet.next());

			return groups;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch groups from repository", e);
		}
	}

	@Override
	public List<GroupData> getGroupsByOwner(String owner) throws DataException {
		List<GroupData> groups = new ArrayList<>();

		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_name, description, created, updated, reference, is_open FROM AccountGroups WHERE owner = ?", owner)) {
			if (resultSet == null)
				return groups;

			do {
				String groupName = resultSet.getString(1);
				String description = resultSet.getString(2);
				long created = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				// Special handling for possibly-NULL "updated" column
				Timestamp updatedTimestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC));
				Long updated = updatedTimestamp == null ? null : updatedTimestamp.getTime();

				byte[] reference = resultSet.getBytes(5);
				boolean isOpen = resultSet.getBoolean(6);

				groups.add(new GroupData(owner, groupName, description, created, updated, isOpen, reference));
			} while (resultSet.next());

			return groups;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's groups from repository", e);
		}
	}

	@Override
	public void save(GroupData groupData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountGroups");

		// Special handling for "updated" timestamp
		Long updated = groupData.getUpdated();
		Timestamp updatedTimestamp = updated == null ? null : new Timestamp(updated);

		saveHelper.bind("owner", groupData.getOwner()).bind("group_name", groupData.getGroupName())
				.bind("description", groupData.getDescription()).bind("created", new Timestamp(groupData.getCreated())).bind("updated", updatedTimestamp)
				.bind("reference", groupData.getReference()).bind("is_open", groupData.getIsOpen());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group info into repository", e);
		}
	}

	@Override
	public void delete(String groupName) throws DataException {
		try {
			this.repository.delete("AccountGroups", "group_name = ?", groupName);
		} catch (SQLException e) {
			throw new DataException("Unable to delete group info from repository", e);
		}
	}

}
