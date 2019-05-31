package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(
	allOf = {
		TransactionData.class
	}
)
public class GroupApprovalTransactionData extends TransactionData {

	// Properties
	@Schema(
		description = "admin's public key",
		example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP"
	)
	private byte[] adminPublicKey;
	@Schema(
		description = "transaction pending approval",
		example = "transaction_signature"
	)
	private byte[] pendingSignature;
	@Schema(
		description = "approval decision",
		example = "true"
	)
	private boolean approval;
	/** Reference to prior GROUP_APPROVAL transaction, used to rebuild approval status during orphaning. */
	// For internal use when orphaning
	@XmlTransient
	@Schema(
		hidden = true
	)
	private byte[] priorReference;

	// Constructors

	// For JAXB
	protected GroupApprovalTransactionData() {
		super(TransactionType.GROUP_APPROVAL);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	/** From repository */
	public GroupApprovalTransactionData(long timestamp, int groupId, byte[] reference, byte[] adminPublicKey, byte[] pendingSignature, boolean approval,
			byte[] priorReference, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.GROUP_APPROVAL, timestamp, groupId, reference, adminPublicKey, fee, approvalStatus, height, signature);

		this.adminPublicKey = adminPublicKey;
		this.pendingSignature = pendingSignature;
		this.approval = approval;
		this.priorReference = priorReference;
	}

	/** From network/API */
	public GroupApprovalTransactionData(long timestamp, int groupId, byte[] reference, byte[] adminPublicKey, byte[] pendingSignature, boolean approval,
			BigDecimal fee, byte[] signature) {
		this(timestamp, groupId, reference, adminPublicKey, pendingSignature, approval, null, fee, null, null, signature);
	}

	/** New, unsigned */
	public GroupApprovalTransactionData(long timestamp, int groupId, byte[] reference, byte[] adminPublicKey, byte[] pendingSignature, boolean approval,
			BigDecimal fee) {
		this(timestamp, groupId, reference, adminPublicKey, pendingSignature, approval, fee, null);
	}

	// Getters / setters

	public byte[] getAdminPublicKey() {
		return this.adminPublicKey;
	}

	public byte[] getPendingSignature() {
		return this.pendingSignature;
	}

	public boolean getApproval() {
		return this.approval;
	}

	public byte[] getPriorReference() {
		return this.priorReference;
	}

	public void setPriorReference(byte[] priorReference) {
		this.priorReference = priorReference;
	}

}
