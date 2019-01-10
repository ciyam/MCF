package org.qora.data.group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupMemberData {

	// Properties
	private String groupName;
	private String member;
	private long joined;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupMemberData() {
	}

	public GroupMemberData(String groupName, String member, long joined) {
		this.groupName = groupName;
		this.member = member;
		this.joined = joined;
	}

	// Getters / setters

	public String getGroupName() {
		return this.groupName;
	}

	public String getMember() {
		return this.member;
	}

	public long getJoined() {
		return this.joined;
	}

}
