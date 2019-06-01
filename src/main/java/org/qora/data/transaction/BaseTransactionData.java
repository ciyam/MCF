package org.qora.data.transaction;

import java.math.BigDecimal;

import org.qora.transaction.Transaction.ApprovalStatus;

public class BaseTransactionData extends TransactionData {

	/** Constructor for use by repository. */
	public BaseTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, 
			ApprovalStatus approvalStatus, Integer blockHeight, Integer approvalHeight, byte[] signature) {
		this.timestamp = timestamp;
		this.txGroupId = txGroupId;
		this.reference = reference;
		this.creatorPublicKey = creatorPublicKey;
		this.fee = fee;
		this.approvalStatus = approvalStatus;
		this.blockHeight = blockHeight;
		this.approvalHeight = approvalHeight;
		this.signature = signature;
	}

	/** Constructor for use by transaction transformer. */
	public BaseTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] creatorPublicKey, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, creatorPublicKey, fee, null, null, null, signature);
	}

}
