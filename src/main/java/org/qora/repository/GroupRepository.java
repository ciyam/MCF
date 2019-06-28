package org.qora.repository;

import java.util.List;

import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupBanData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupInviteData;
import org.qora.data.group.GroupJoinRequestData;
import org.qora.data.group.GroupMemberData;

public interface GroupRepository {

	// Groups

	public GroupData fromGroupId(int groupId) throws DataException;

	public GroupData fromGroupName(String groupName) throws DataException;

	public boolean groupExists(int groupId) throws DataException;

	public boolean groupExists(String groupName) throws DataException;

	public List<GroupData> getAllGroups() throws DataException;

	public List<GroupData> getGroupsByOwner(String address) throws DataException;

	public List<GroupData> getGroupsWithMember(String member) throws DataException;

	public void save(GroupData groupData) throws DataException;

	public void delete(int groupId) throws DataException;

	public void delete(String groupName) throws DataException;

	// Group Admins

	public GroupAdminData getAdmin(int groupId, String address) throws DataException;

	public boolean adminExists(int groupId, String address) throws DataException;

	public List<GroupAdminData> getGroupAdmins(int groupId) throws DataException;

	public void save(GroupAdminData groupAdminData) throws DataException;

	public void deleteAdmin(int groupId, String address) throws DataException;

	// Group Members

	public GroupMemberData getMember(int groupId, String address) throws DataException;

	public boolean memberExists(int groupId, String address) throws DataException;

	public List<GroupMemberData> getGroupMembers(int groupId) throws DataException;

	/** Returns number of group members, or null if group doesn't exist */
	public Integer countGroupMembers(int groupId) throws DataException;

	public void save(GroupMemberData groupMemberData) throws DataException;

	public void deleteMember(int groupId, String address) throws DataException;

	// Group Invites

	public GroupInviteData getInvite(int groupId, String invitee) throws DataException;

	public boolean inviteExists(int groupId, String invitee) throws DataException;

	public List<GroupInviteData> getGroupInvites(int groupId) throws DataException;

	public List<GroupInviteData> getInvitesByInvitee(String invitee) throws DataException;

	public void save(GroupInviteData groupInviteData) throws DataException;

	public void deleteInvite(int groupId, String invitee) throws DataException;

	// Group Join Requests

	public GroupJoinRequestData getJoinRequest(Integer groupId, String joiner) throws DataException;

	public boolean joinRequestExists(int groupId, String joiner) throws DataException;

	public List<GroupJoinRequestData> getGroupJoinRequests(int groupId) throws DataException;

	public void save(GroupJoinRequestData groupJoinRequestData) throws DataException;

	public void deleteJoinRequest(int groupId, String joiner) throws DataException;

	// Group Bans

	public GroupBanData getBan(int groupId, String member) throws DataException;

	public boolean banExists(int groupId, String offender) throws DataException;

	public List<GroupBanData> getGroupBans(int groupId) throws DataException;

	public void save(GroupBanData groupBanData) throws DataException;

	public void deleteBan(int groupId, String offender) throws DataException;

}