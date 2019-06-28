package org.qora.data.naming;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class NameData {

	// Properties
	private byte[] registrantPublicKey;
	private String owner;
	private String name;
	private String data;
	private long registered;
	private Long updated;
	private byte[] reference;
	private boolean isForSale;
	private BigDecimal salePrice;

	// Constructors

	// necessary for JAX-RS serialization
	protected NameData() {
	}

	public NameData(byte[] registrantPublicKey, String owner, String name, String data, long registered, Long updated, byte[] reference, boolean isForSale,
			BigDecimal salePrice) {
		this.registrantPublicKey = registrantPublicKey;
		this.owner = owner;
		this.name = name;
		this.data = data;
		this.registered = registered;
		this.updated = updated;
		this.reference = reference;
		this.isForSale = isForSale;
		this.salePrice = salePrice;
	}

	public NameData(byte[] registrantPublicKey, String owner, String name, String data, long registered, byte[] reference) {
		this(registrantPublicKey, owner, name, data, registered, null, reference, false, null);
	}

	// Getters / setters

	public byte[] getRegistrantPublicKey() {
		return this.registrantPublicKey;
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

	public String getData() {
		return this.data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public long getRegistered() {
		return this.registered;
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

	public boolean getIsForSale() {
		return this.isForSale;
	}

	public void setIsForSale(boolean isForSale) {
		this.isForSale = isForSale;
	}

	public BigDecimal getSalePrice() {
		return this.salePrice;
	}

	public void setSalePrice(BigDecimal salePrice) {
		this.salePrice = salePrice;
	}

}
