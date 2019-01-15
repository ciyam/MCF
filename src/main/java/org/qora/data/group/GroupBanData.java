package org.qora.data.group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupBanData {

	// Properties
	private String groupName;
	private String offender;
	private String admin;
	private long banned;
	private String reason;
	private Long expiry;
	// No need to ever expose this via API
	@XmlTransient
	private byte[] reference;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupBanData() {
	}

	public GroupBanData(String groupName, String offender, String admin, long banned, String reason, Long expiry, byte[] reference) {
		this.groupName = groupName;
		this.offender = offender;
		this.admin = admin;
		this.banned = banned;
		this.reason = reason;
		this.expiry = expiry;
		this.reference = reference;
	}

	// Getters / setters

	public String getGroupName() {
		return this.groupName;
	}

	public String getOffender() {
		return this.offender;
	}

	public String getAdmin() {
		return this.admin;
	}

	public long getBanned() {
		return this.banned;
	}

	public String getReason() {
		return this.reason;
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
