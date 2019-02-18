package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class RegisterNameTransactionData extends TransactionData {

	// Properties
	@Schema(description = "registrant's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] registrantPublicKey;
	@Schema(description = "new owner's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String owner;
	@Schema(description = "requested name", example = "my-name")
	private String name;
	@Schema(description = "simple name-related info in JSON format", example = "{ \"age\": 30 }")
	private String data;

	// Constructors

	// For JAXB
	protected RegisterNameTransactionData() {
		super(TransactionType.REGISTER_NAME);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.registrantPublicKey;
	}

	public RegisterNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] registrantPublicKey, String owner, String name, String data,
			BigDecimal fee, byte[] signature) {
		super(TransactionType.REGISTER_NAME, timestamp, txGroupId, reference, registrantPublicKey, fee, signature);

		this.registrantPublicKey = registrantPublicKey;
		this.owner = owner;
		this.name = name;
		this.data = data;
	}

	public RegisterNameTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] registrantPublicKey, String owner, String name, String data, BigDecimal fee) {
		this(timestamp, txGroupId, reference, registrantPublicKey, owner, name, data, fee, null);
	}

	// Getters / setters

	public byte[] getRegistrantPublicKey() {
		return this.registrantPublicKey;
	}

	public String getOwner() {
		return this.owner;
	}

	public String getName() {
		return this.name;
	}

	public String getData() {
		return this.data;
	}

}
