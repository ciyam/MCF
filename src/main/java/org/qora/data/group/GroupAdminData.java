package org.qora.data.group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupAdminData {

	// Properties
	private String groupName;
	private String admin;
	/** Reference to transaction that triggered adminship */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] groupReference;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupAdminData() {
	}

	public GroupAdminData(String groupName, String admin, byte[] groupReference) {
		this.groupName = groupName;
		this.admin = admin;
		this.groupReference = groupReference;
	}

	// Getters / setters

	public String getGroupName() {
		return this.groupName;
	}

	public String getAdmin() {
		return this.admin;
	}

	public byte[] getGroupReference() {
		return this.groupReference;
	}

	public void setGroupReference(byte[] groupReference) {
		this.groupReference = groupReference;
	}

}
