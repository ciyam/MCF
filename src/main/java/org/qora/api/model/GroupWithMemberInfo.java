package org.qora.api.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.data.group.GroupAdminData;
import org.qora.data.group.GroupData;
import org.qora.data.group.GroupMemberData;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Group info, maybe including members")
// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupWithMemberInfo {

	@Schema(implementation = GroupData.class, name = "group", title = "group info")
	@XmlElement(name = "group")
	public GroupData groupData;

	Integer memberCount;

	public List<GroupAdminData> groupAdmins;
	public List<GroupMemberData> groupMembers;

	// For JAX-RS
	protected GroupWithMemberInfo() {
	}

	public GroupWithMemberInfo(GroupData groupData, List<GroupAdminData> groupAdmins, List<GroupMemberData> groupMembers, Integer memberCount) {
		this.groupData = groupData;
		this.groupAdmins = groupAdmins;
		this.groupMembers = groupMembers;
		this.memberCount = memberCount;
	}

}
