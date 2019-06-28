package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
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

	// For JAX-RS
	protected RegisterNameTransactionData() {
		super(TransactionType.REGISTER_NAME);
	}

	public RegisterNameTransactionData(byte[] registrantPublicKey, String owner, String name, String data, BigDecimal fee, long timestamp, byte[] reference,
			byte[] signature) {
		super(TransactionType.REGISTER_NAME, fee, registrantPublicKey, timestamp, reference, signature);

		this.registrantPublicKey = registrantPublicKey;
		this.owner = owner;
		this.name = name;
		this.data = data;
	}

	public RegisterNameTransactionData(byte[] registrantPublicKey, String owner, String name, String data, BigDecimal fee, long timestamp, byte[] reference) {
		this(registrantPublicKey, owner, name, data, fee, timestamp, reference, null);
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
