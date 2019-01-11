package org.qora.data.group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupMemberData {

	// Properties
	private String groupName;
	private String member;
	private long joined;
	/** Reference to transaction that triggered membership */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] groupReference;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupMemberData() {
	}

	public GroupMemberData(String groupName, String member, long joined, byte[] groupReference) {
		this.groupName = groupName;
		this.member = member;
		this.joined = joined;
		this.groupReference = groupReference;
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

	public byte[] getGroupReference() {
		return this.groupReference;
	}

	public void setGroupReference(byte[] groupReference) {
		this.groupReference = groupReference;
	}

}
