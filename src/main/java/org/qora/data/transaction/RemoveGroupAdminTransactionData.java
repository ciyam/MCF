package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class RemoveGroupAdminTransactionData extends TransactionData {

	// Properties
	@Schema(description = "group owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "group name", example = "my-group")
	private String groupName;
	@Schema(description = "admin to demote", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String admin; 
	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private byte[] groupReference;

	// Constructors

	// For JAX-RS
	protected RemoveGroupAdminTransactionData() {
		super(TransactionType.REMOVE_GROUP_ADMIN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	public RemoveGroupAdminTransactionData(byte[] ownerPublicKey, String groupName, String admin, byte[] groupReference, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.REMOVE_GROUP_ADMIN, fee, ownerPublicKey, timestamp, reference, signature);

		this.ownerPublicKey = ownerPublicKey;
		this.groupName = groupName;
		this.admin = admin;
		this.groupReference = groupReference;
	}

	public RemoveGroupAdminTransactionData(byte[] ownerPublicKey, String groupName, String admin, byte[] groupReference, BigDecimal fee, long timestamp, byte[] reference) {
		this(ownerPublicKey, groupName, admin, groupReference, fee, timestamp, reference, null);
	}

	public RemoveGroupAdminTransactionData(byte[] ownerPublicKey, String groupName, String admin, BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		this(ownerPublicKey, groupName, admin, null, fee, timestamp, reference, signature);
	}

	public RemoveGroupAdminTransactionData(byte[] ownerPublicKey, String groupName, String admin, BigDecimal fee, long timestamp, byte[] reference) {
		this(ownerPublicKey, groupName, admin, null, fee, timestamp, reference, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

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
