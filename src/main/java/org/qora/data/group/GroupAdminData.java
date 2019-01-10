package org.qora.data.group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupAdminData {

	// Properties
	private String groupName;
	private String admin;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupAdminData() {
	}

	public GroupAdminData(String groupName, String admin) {
		this.groupName = groupName;
		this.admin = admin;
	}

	// Getters / setters

	public String getGroupName() {
		return this.groupName;
	}

	public String getAdmin() {
		return this.admin;
	}

}
