package org.qora.repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupMemberData;
import org.qora.repository.DataException;
import org.qora.repository.GroupRepository;

public class HSQLDBGroupRepository implements GroupRepository {

	protected HSQLDBRepository repository;

	public HSQLDBGroupRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	// Groups

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
			// Remove invites
			this.repository.delete("AccountGroupInvites", "group_name = ?", groupName);
			// Remove bans
			this.repository.delete("AccountGroupBans", "group_name = ?", groupName);
			// Remove members
			this.repository.delete("AccountGroupMembers", "group_name = ?", groupName);
			// Remove admins
			this.repository.delete("AccountGroupAdmins", "group_name = ?", groupName);
			// Remove group
			this.repository.delete("AccountGroups", "group_name = ?", groupName);
		} catch (SQLException e) {
			throw new DataException("Unable to delete group info from repository", e);
		}
	}

	// Group Admins

	@Override
	public List<GroupAdminData> getAllGroupAdmins(String groupName) throws DataException {
		List<GroupAdminData> admins = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT admin FROM AccountGroupAdmins WHERE group_name = ?", groupName)) {
			if (resultSet == null)
				return admins;

			do {
				String admin = resultSet.getString(1);

				admins.add(new GroupAdminData(groupName, admin));
			} while (resultSet.next());

			return admins;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group admins from repository", e);
		}
	}

	@Override
	public void save(GroupAdminData groupAdminData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountGroupAdmins");

		saveHelper.bind("group_name", groupAdminData.getGroupName()).bind("admin", groupAdminData.getAdmin());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group admin info into repository", e);
		}
	}

	@Override
	public void delete(GroupAdminData groupAdminData) throws DataException {
		try {
			this.repository.delete("AccountGroupAdmins", "group_name = ? AND admin = ?", groupAdminData.getGroupName(), groupAdminData.getAdmin());
		} catch (SQLException e) {
			throw new DataException("Unable to delete group admin info from repository", e);
		}
	}

	// Group Members

	@Override
	public boolean memberExists(String groupName, String member) throws DataException {
		try {
			return this.repository.exists("AccountGroupMembers", "group_name = ? AND address = ?", groupName, member);
		} catch (SQLException e) {
			throw new DataException("Unable to check for group member in repository", e);
		}
	}

	@Override
	public List<GroupMemberData> getAllGroupMembers(String groupName) throws DataException {
		List<GroupMemberData> members = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT address, joined FROM AccountGroupMembers WHERE group_name = ?", groupName)) {
			if (resultSet == null)
				return members;

			do {
				String member = resultSet.getString(1);
				long joined = resultSet.getTimestamp(2, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();

				members.add(new GroupMemberData(groupName, member, joined));
			} while (resultSet.next());

			return members;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group members from repository", e);
		}
	}

	@Override
	public Integer countGroupMembers(String groupName) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT group_name, COUNT(*) FROM AccountGroupMembers WHERE group_name = ? GROUP BY group_name", groupName)) {
			if (resultSet == null)
				return null;

			return resultSet.getInt(2);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group member count from repository", e);
		}
	}

	@Override
	public void save(GroupMemberData groupMemberData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountGroupMembers");

		saveHelper.bind("group_name", groupMemberData.getGroupName()).bind("address", groupMemberData.getMember()).bind("joined", new Timestamp(groupMemberData.getJoined()));

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group member info into repository", e);
		}
	}

	@Override
	public void delete(GroupMemberData groupMemberData) throws DataException {
		try {
			this.repository.delete("AccountGroupMembers", "group_name = ? AND address = ?", groupMemberData.getGroupName(), groupMemberData.getMember());
		} catch (SQLException e) {
			throw new DataException("Unable to delete group member info from repository", e);
		}
	}

}
