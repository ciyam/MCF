package org.qora.data.asset;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class AssetData {

	// Properties
	private Long assetId;
	private String owner;
	private String name;
	private String description;
	private long quantity;
	private boolean isDivisible;
	private String data;
	private int creationGroupId;
	// No need to expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] reference;

	// Constructors

	// necessary for JAX-RS serialization
	protected AssetData() {
	}

	// NOTE: key is Long, not long, because it can be null if asset ID/key not yet assigned.
	public AssetData(Long assetId, String owner, String name, String description, long quantity, boolean isDivisible, String data, int creationGroupId, byte[] reference) {
		this.assetId = assetId;
		this.owner = owner;
		this.name = name;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
		this.data = data;
		this.creationGroupId = creationGroupId;
		this.reference = reference;
	}

	// New asset with unassigned assetId
	public AssetData(String owner, String name, String description, long quantity, boolean isDivisible, String data, int creationGroupId, byte[] reference) {
		this(null, owner, name, description, quantity, isDivisible, data, creationGroupId, reference);
	}

	// Getters/Setters

	public Long getAssetId() {
		return this.assetId;
	}

	public void setAssetId(Long assetId) {
		this.assetId = assetId;
	}

	public String getOwner() {
		return this.owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public long getQuantity() {
		return this.quantity;
	}

	public boolean getIsDivisible() {
		return this.isDivisible;
	}

	public String getData() {
		return this.data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public int getCreationGroupId() {
		return this.creationGroupId;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

}
