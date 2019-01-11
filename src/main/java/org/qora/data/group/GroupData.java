package org.qora.data.group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupData {

	// Properties
	private String owner;
	private String groupName;
	private String description;
	private long created;
	private Long updated;
	private boolean isOpen;
	/** Reference to transaction that created group */
	// No need to ever expose this via API
	@XmlTransient
	private byte[] reference;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupData() {
	}

	public GroupData(String owner, String name, String description, long created, Long updated, boolean isOpen, byte[] reference) {
		this.owner = owner;
		this.groupName = name;
		this.description = description;
		this.created = created;
		this.updated = updated;
		this.isOpen = isOpen;
		this.reference = reference;
	}

	public GroupData(String owner, String name, String description, long created, boolean isOpen, byte[] reference) {
		this(owner, name, description, created, null, isOpen, reference);
	}

	// Getters / setters

	public String getOwner() {
		return this.owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getGroupName() {
		return this.groupName;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public long getCreated() {
		return this.created;
	}

	public Long getUpdated() {
		return this.updated;
	}

	public void setUpdated(Long updated) {
		this.updated = updated;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

	public boolean getIsOpen() {
		return this.isOpen;
	}

	public void setIsOpen(boolean isOpen) {
		this.isOpen = isOpen;
	}

}
