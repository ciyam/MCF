package org.qora.data.group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupJoinRequestData {

	// Properties
	private String groupName;
	private String joiner;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupJoinRequestData() {
	}

	public GroupJoinRequestData(String groupName, String joiner) {
		this.groupName = groupName;
		this.joiner = joiner;
	}

	// Getters / setters

	public String getGroupName() {
		return this.groupName;
	}

	public String getJoiner() {
		return this.joiner;
	}

}
