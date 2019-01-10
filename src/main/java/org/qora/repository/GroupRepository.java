package org.qora.repository;

import java.util.List;

import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupData;
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

	public List<GroupAdminData> getAllGroupAdmins(String groupName) throws DataException;

	public void save(GroupAdminData groupAdminData) throws DataException;

	public void delete(GroupAdminData groupAdminData) throws DataException;

	// Group Members

	public boolean memberExists(String groupName, String member) throws DataException;

	public List<GroupMemberData> getAllGroupMembers(String groupName) throws DataException;

	/** Returns number of group members, or null if group doesn't exist */
	public Integer countGroupMembers(String groupName) throws DataException;

	public void save(GroupMemberData groupMemberData) throws DataException;

	public void delete(GroupMemberData groupMemberData) throws DataException;

}