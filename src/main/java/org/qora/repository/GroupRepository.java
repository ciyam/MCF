package org.qora.repository;

import java.util.List;

import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupInviteData;
import org.qora.data.group.GroupJoinRequestData;
import org.qora.data.group.GroupMemberData;

public interface GroupRepository {

	// Groups

	public GroupData fromGroupName(String groupName) throws DataException;

	public boolean groupExists(String groupName) throws DataException;

	public List<GroupData> getAllGroups() throws DataException;

	public List<GroupData> getGroupsByOwner(String address) throws DataException;

	public void save(GroupData groupData) throws DataException;

	public void delete(String groupName) throws DataException;

	// Group Admins

	public GroupAdminData getAdmin(String groupName, String address) throws DataException;

	public boolean adminExists(String groupName, String address) throws DataException;

	public List<GroupAdminData> getGroupAdmins(String groupName) throws DataException;

	public void save(GroupAdminData groupAdminData) throws DataException;

	public void deleteAdmin(String groupName, String address) throws DataException;

	// Group Members

	public GroupMemberData getMember(String groupName, String address) throws DataException;

	public boolean memberExists(String groupName, String address) throws DataException;

	public List<GroupMemberData> getGroupMembers(String groupName) throws DataException;

	/** Returns number of group members, or null if group doesn't exist */
	public Integer countGroupMembers(String groupName) throws DataException;

	public void save(GroupMemberData groupMemberData) throws DataException;

	public void deleteMember(String groupName, String address) throws DataException;

	// Group Invites

	public GroupInviteData getInvite(String groupName, String inviter, String invitee) throws DataException;

	public boolean hasInvite(String groupName, String invitee) throws DataException;

	public boolean inviteExists(String groupName, String inviter, String invitee) throws DataException;

	public List<GroupInviteData> getGroupInvites(String groupName) throws DataException;

	public List<GroupInviteData> getInvitesByInvitee(String groupName, String invitee) throws DataException;

	public void save(GroupInviteData groupInviteData) throws DataException;

	public void deleteInvite(String groupName, String inviter, String invitee) throws DataException;

	// Group Join Requests

	public boolean joinRequestExists(String groupName, String joiner) throws DataException;

	public List<GroupJoinRequestData> getGroupJoinRequests(String groupName) throws DataException;

	public void save(GroupJoinRequestData groupJoinRequestData) throws DataException;

	public void deleteJoinRequest(String groupName, String joiner) throws DataException;

}