package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.block.BlockChain;
import org.qora.data.transaction.GroupApprovalTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

public class GroupApprovalTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int PENDING_SIGNATURE_LENGTH = SIGNATURE_LENGTH;
	private static final int APPROVAL_LENGTH = BOOLEAN_LENGTH;

	private static final int EXTRAS_LENGTH = PENDING_SIGNATURE_LENGTH + APPROVAL_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.GROUP_INVITE.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group admin's public key", TransformationType.PUBLIC_KEY);
		layout.add("pending transaction's signature", TransformationType.SIGNATURE);
		layout.add("approval decision", TransformationType.BOOLEAN);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = 0;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] adminPublicKey = Serialization.deserializePublicKey(byteBuffer);

		byte[] pendingSignature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(pendingSignature);

		boolean approval = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new GroupApprovalTransactionData(timestamp, txGroupId, reference, adminPublicKey, pendingSignature, approval, fee, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return getBaseLength(transactionData) + EXTRAS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GroupApprovalTransactionData groupApprovalTransactionData = (GroupApprovalTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			bytes.write(groupApprovalTransactionData.getPendingSignature());

			bytes.write((byte) (groupApprovalTransactionData.getApproval() ? 1 : 0));

			Serialization.serializeBigDecimal(bytes, groupApprovalTransactionData.getFee());

			if (groupApprovalTransactionData.getSignature() != null)
				bytes.write(groupApprovalTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
