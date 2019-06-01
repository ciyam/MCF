package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.block.BlockChain;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.primitives.Ints;

public class JoinGroupTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int GROUPID_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = GROUPID_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.JOIN_GROUP.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("joiner's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
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

		byte[] joinerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, joinerPublicKey, fee, signature);

		return new JoinGroupTransactionData(baseTransactionData, groupId);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return getBaseLength(transactionData) + EXTRAS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			JoinGroupTransactionData joinGroupTransactionData = (JoinGroupTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			bytes.write(Ints.toByteArray(joinGroupTransactionData.getGroupId()));

			Serialization.serializeBigDecimal(bytes, joinGroupTransactionData.getFee());

			if (joinGroupTransactionData.getSignature() != null)
				bytes.write(joinGroupTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
