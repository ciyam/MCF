package org.qora.repository;

import java.util.List;

import org.qora.data.group.GroupData;

public interface GroupRepository {

	public GroupData fromGroupName(String groupName) throws DataException;

	public boolean groupExists(String groupName) throws DataException;

	public List<GroupData> getAllGroups() throws DataException;

	public List<GroupData> getGroupsByOwner(String address) throws DataException;

	public void save(GroupData groupData) throws DataException;

	public void delete(String groupName) throws DataException;

}
