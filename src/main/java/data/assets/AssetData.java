package data.assets;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

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
	private byte[] reference;

	// Constructors

	// necessary for JAX-RS serialization
	protected AssetData() {
	}

	// NOTE: key is Long, not long, because it can be null if asset ID/key not yet assigned.
	public AssetData(Long assetId, String owner, String name, String description, long quantity, boolean isDivisible, byte[] reference) {
		this.assetId = assetId;
		this.owner = owner;
		this.name = name;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
		this.reference = reference;
	}

	// New asset with unassigned assetId
	public AssetData(String owner, String name, String description, long quantity, boolean isDivisible, byte[] reference) {
		this(null, owner, name, description, quantity, isDivisible, reference);
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

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public long getQuantity() {
		return this.quantity;
	}

	public boolean getIsDivisible() {
		return this.isDivisible;
	}

	public byte[] getReference() {
		return this.reference;
	}

}
