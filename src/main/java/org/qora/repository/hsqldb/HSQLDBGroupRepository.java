package org.qora.repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupBanData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupInviteData;
import org.qora.data.group.GroupJoinRequestData;
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

		saveHelper.bind("owner", groupData.getOwner()).bind("group_name", groupData.getGroupName()).bind("description", groupData.getDescription())
				.bind("created", new Timestamp(groupData.getCreated())).bind("updated", updatedTimestamp).bind("reference", groupData.getReference())
				.bind("is_open", groupData.getIsOpen());

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
	public GroupAdminData getAdmin(String groupName, String address) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT admin, group_reference FROM AccountGroupAdmins WHERE group_name = ?", groupName)) {
			if (resultSet == null)
				return null;

			String admin = resultSet.getString(1);
			byte[] groupReference = resultSet.getBytes(2);

			return new GroupAdminData(groupName, admin, groupReference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group admin from repository", e);
		}
	}

	@Override
	public boolean adminExists(String groupName, String address) throws DataException {
		try {
			return this.repository.exists("AccountGroupAdmins", "group_name = ? AND admin = ?", groupName, address);
		} catch (SQLException e) {
			throw new DataException("Unable to check for group admin in repository", e);
		}
	}

	@Override
	public List<GroupAdminData> getGroupAdmins(String groupName) throws DataException {
		List<GroupAdminData> admins = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT admin, group_reference FROM AccountGroupAdmins WHERE group_name = ?", groupName)) {
			if (resultSet == null)
				return admins;

			do {
				String admin = resultSet.getString(1);
				byte[] groupReference = resultSet.getBytes(2);

				admins.add(new GroupAdminData(groupName, admin, groupReference));
			} while (resultSet.next());

			return admins;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group admins from repository", e);
		}
	}

	@Override
	public void save(GroupAdminData groupAdminData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountGroupAdmins");

		saveHelper.bind("group_name", groupAdminData.getGroupName()).bind("admin", groupAdminData.getAdmin()).bind("group_reference",
				groupAdminData.getGroupReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group admin info into repository", e);
		}
	}

	@Override
	public void deleteAdmin(String groupName, String address) throws DataException {
		try {
			this.repository.delete("AccountGroupAdmins", "group_name = ? AND admin = ?", groupName, address);
		} catch (SQLException e) {
			throw new DataException("Unable to delete group admin info from repository", e);
		}
	}

	// Group Members

	@Override
	public GroupMemberData getMember(String groupName, String address) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT address, joined, group_reference FROM AccountGroupMembers WHERE group_name = ?",
				groupName)) {
			if (resultSet == null)
				return null;

			String member = resultSet.getString(1);
			long joined = resultSet.getTimestamp(2, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			byte[] groupReference = resultSet.getBytes(3);

			return new GroupMemberData(groupName, member, joined, groupReference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group members from repository", e);
		}
	}

	@Override
	public boolean memberExists(String groupName, String address) throws DataException {
		try {
			return this.repository.exists("AccountGroupMembers", "group_name = ? AND address = ?", groupName, address);
		} catch (SQLException e) {
			throw new DataException("Unable to check for group member in repository", e);
		}
	}

	@Override
	public List<GroupMemberData> getGroupMembers(String groupName) throws DataException {
		List<GroupMemberData> members = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT address, joined, group_reference FROM AccountGroupMembers WHERE group_name = ?",
				groupName)) {
			if (resultSet == null)
				return members;

			do {
				String member = resultSet.getString(1);
				long joined = resultSet.getTimestamp(2, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
				byte[] groupReference = resultSet.getBytes(3);

				members.add(new GroupMemberData(groupName, member, joined, groupReference));
			} while (resultSet.next());

			return members;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group members from repository", e);
		}
	}

	@Override
	public Integer countGroupMembers(String groupName) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT group_name, COUNT(*) FROM AccountGroupMembers WHERE group_name = ? GROUP BY group_name", groupName)) {
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

		saveHelper.bind("group_name", groupMemberData.getGroupName()).bind("address", groupMemberData.getMember())
				.bind("joined", new Timestamp(groupMemberData.getJoined())).bind("group_reference", groupMemberData.getGroupReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group member info into repository", e);
		}
	}

	@Override
	public void deleteMember(String groupName, String address) throws DataException {
		try {
			this.repository.delete("AccountGroupMembers", "group_name = ? AND address = ?", groupName, address);
		} catch (SQLException e) {
			throw new DataException("Unable to delete group member info from repository", e);
		}
	}

	// Group Invites

	@Override
	public GroupInviteData getInvite(String groupName, String inviter, String invitee) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute("SELECT expiry, reference FROM AccountGroupInvites WHERE group_name = ?", groupName)) {
			if (resultSet == null)
				return null;

			Timestamp expiryTimestamp = resultSet.getTimestamp(1, Calendar.getInstance(HSQLDBRepository.UTC));
			Long expiry = expiryTimestamp == null ? null : expiryTimestamp.getTime();

			byte[] reference = resultSet.getBytes(2);

			return new GroupInviteData(groupName, inviter, invitee, expiry, reference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group invite from repository", e);
		}
	}

	@Override
	public boolean inviteExists(String groupName, String invitee) throws DataException {
		try {
			return this.repository.exists("AccountGroupInvites", "group_name = ? AND invitee = ?", groupName, invitee);
		} catch (SQLException e) {
			throw new DataException("Unable to check for group invite in repository", e);
		}
	}

	@Override
	public boolean inviteExists(String groupName, String inviter, String invitee) throws DataException {
		try {
			return this.repository.exists("AccountGroupInvites", "group_name = ? AND inviter = ? AND invitee = ?", groupName, inviter, invitee);
		} catch (SQLException e) {
			throw new DataException("Unable to check for group invite in repository", e);
		}
	}

	@Override
	public List<GroupInviteData> getGroupInvites(String groupName) throws DataException {
		List<GroupInviteData> invites = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT inviter, invitee, expiry, reference FROM AccountGroupInvites WHERE group_name = ?",
				groupName)) {
			if (resultSet == null)
				return invites;

			do {
				String inviter = resultSet.getString(1);
				String invitee = resultSet.getString(2);

				Timestamp expiryTimestamp = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC));
				Long expiry = expiryTimestamp == null ? null : expiryTimestamp.getTime();

				byte[] reference = resultSet.getBytes(4);

				invites.add(new GroupInviteData(groupName, inviter, invitee, expiry, reference));
			} while (resultSet.next());

			return invites;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group invites from repository", e);
		}
	}

	@Override
	public List<GroupInviteData> getInvitesByInvitee(String groupName, String invitee) throws DataException {
		List<GroupInviteData> invites = new ArrayList<>();

		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT inviter, expiry, reference FROM AccountGroupInvites WHERE group_name = ? AND invitee = ?", groupName, invitee)) {
			if (resultSet == null)
				return invites;

			do {
				String inviter = resultSet.getString(1);

				Timestamp expiryTimestamp = resultSet.getTimestamp(2, Calendar.getInstance(HSQLDBRepository.UTC));
				Long expiry = expiryTimestamp == null ? null : expiryTimestamp.getTime();

				byte[] reference = resultSet.getBytes(3);

				invites.add(new GroupInviteData(groupName, inviter, invitee, expiry, reference));
			} while (resultSet.next());

			return invites;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group invites from repository", e);
		}
	}

	@Override
	public void save(GroupInviteData groupInviteData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountGroupInvites");

		Timestamp expiryTimestamp = null;
		if (groupInviteData.getExpiry() != null)
			expiryTimestamp = new Timestamp(groupInviteData.getExpiry());

		saveHelper.bind("group_name", groupInviteData.getGroupName()).bind("inviter", groupInviteData.getInviter())
				.bind("invitee", groupInviteData.getInvitee()).bind("expiry", expiryTimestamp).bind("reference", groupInviteData.getReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group invite into repository", e);
		}
	}

	@Override
	public void deleteInvite(String groupName, String inviter, String invitee) throws DataException {
		try {
			this.repository.delete("AccountGroupInvites", "group_name = ? AND inviter = ? AND invitee = ?", groupName, inviter, invitee);
		} catch (SQLException e) {
			throw new DataException("Unable to delete group invite from repository", e);
		}
	}

	// Group Join Requests

	@Override
	public boolean joinRequestExists(String groupName, String joiner) throws DataException {
		try {
			return this.repository.exists("AccountGroupJoinRequests", "group_name = ? AND joiner = ?", groupName, joiner);
		} catch (SQLException e) {
			throw new DataException("Unable to check for group join request in repository", e);
		}
	}

	@Override
	public List<GroupJoinRequestData> getGroupJoinRequests(String groupName) throws DataException {
		List<GroupJoinRequestData> joinRequests = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT joiner FROM AccountGroupJoinRequests WHERE group_name = ?", groupName)) {
			if (resultSet == null)
				return joinRequests;

			do {
				String joiner = resultSet.getString(1);

				joinRequests.add(new GroupJoinRequestData(groupName, joiner));
			} while (resultSet.next());

			return joinRequests;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group join requests from repository", e);
		}
	}

	@Override
	public void save(GroupJoinRequestData groupJoinRequestData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountGroupJoinRequests");

		saveHelper.bind("group_name", groupJoinRequestData.getGroupName()).bind("joiner", groupJoinRequestData.getJoiner());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group join request into repository", e);
		}
	}

	@Override
	public void deleteJoinRequest(String groupName, String joiner) throws DataException {
		try {
			this.repository.delete("AccountGroupJoinRequests", "group_name = ? AND joiner = ?", groupName, joiner);
		} catch (SQLException e) {
			throw new DataException("Unable to delete group join request from repository", e);
		}
	}

	// Group Bans

	@Override
	public GroupBanData getBan(String groupName, String member) throws DataException {
		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT offender, admin, banned, reason, expiry, reference FROM AccountGroupBans WHERE group_name = ?", groupName)) {
			String offender = resultSet.getString(1);
			String admin = resultSet.getString(2);
			long banned = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
			String reason = resultSet.getString(4);

			Timestamp expiryTimestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC));
			Long expiry = expiryTimestamp == null ? null : expiryTimestamp.getTime();

			byte[] reference = resultSet.getBytes(6);

			return new GroupBanData(groupName, offender, admin, banned, reason, expiry, reference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group bans from repository", e);
		}
	}

	@Override
	public boolean banExists(String groupName, String offender) throws DataException {
		try {
			return this.repository.exists("AccountGroupBans", "group_name = ? AND offender = ?", groupName, offender);
		} catch (SQLException e) {
			throw new DataException("Unable to check for group ban in repository", e);
		}
	}

	@Override
	public List<GroupBanData> getGroupBans(String groupName) throws DataException {
		List<GroupBanData> bans = new ArrayList<>();

		try (ResultSet resultSet = this.repository
				.checkedExecute("SELECT offender, admin, banned, reason, expiry, reference FROM AccountGroupBans WHERE group_name = ?", groupName)) {
			if (resultSet == null)
				return bans;

			do {
				String offender = resultSet.getString(1);
				String admin = resultSet.getString(2);
				long banned = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC)).getTime();
				String reason = resultSet.getString(4);

				Timestamp expiryTimestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC));
				Long expiry = expiryTimestamp == null ? null : expiryTimestamp.getTime();

				byte[] reference = resultSet.getBytes(6);

				bans.add(new GroupBanData(groupName, offender, admin, banned, reason, expiry, reference));
			} while (resultSet.next());

			return bans;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch group bans from repository", e);
		}
	}

	@Override
	public void save(GroupBanData groupBanData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountGroupBans");

		Timestamp expiryTimestamp = null;
		if (groupBanData.getExpiry() != null)
			expiryTimestamp = new Timestamp(groupBanData.getExpiry());

		saveHelper.bind("group_name", groupBanData.getGroupName()).bind("offender", groupBanData.getOffender()).bind("admin", groupBanData.getAdmin())
				.bind("banned", new Timestamp(groupBanData.getBanned())).bind("reason", groupBanData.getReason()).bind("expiry", expiryTimestamp)
				.bind("reference", groupBanData.getReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save group ban into repository", e);
		}
	}

	@Override
	public void deleteBan(String groupName, String offender) throws DataException {
		try {
			this.repository.delete("AccountGroupBans", "group_name = ? AND offender = ?", groupName, offender);
		} catch (SQLException e) {
			throw new DataException("Unable to delete group ban from repository", e);
		}
	}

}
