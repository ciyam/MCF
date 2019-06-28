package org.qora.data.group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupInviteData {

	// Properties
	private int groupId;
	private String inviter;
	private String invitee;
	private Long expiry;
	/** Reference to GROUP_INVITE transaction, used to rebuild this invite during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] reference;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupInviteData() {
	}

	public GroupInviteData(int groupId, String inviter, String invitee, Long expiry, byte[] reference) {
		this.groupId = groupId;
		this.inviter = inviter;
		this.invitee = invitee;
		this.expiry = expiry;
		this.reference = reference;
	}

	// Getters / setters

	public int getGroupId() {
		return this.groupId;
	}

	public String getInviter() {
		return this.inviter;
	}

	public String getInvitee() {
		return this.invitee;
	}

	public Long getExpiry() {
		return this.expiry;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

}
