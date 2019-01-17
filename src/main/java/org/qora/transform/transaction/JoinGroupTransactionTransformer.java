package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.JoinGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class JoinGroupTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int JOINER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int GROUPID_LENGTH = INT_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + JOINER_LENGTH + GROUPID_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.JOIN_GROUP.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("joiner's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] joinerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new JoinGroupTransactionData(joinerPublicKey, groupId, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			JoinGroupTransactionData joinGroupTransactionData = (JoinGroupTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(joinGroupTransactionData.getType().value));
			bytes.write(Longs.toByteArray(joinGroupTransactionData.getTimestamp()));
			bytes.write(joinGroupTransactionData.getReference());

			bytes.write(joinGroupTransactionData.getCreatorPublicKey());
			bytes.write(Ints.toByteArray(joinGroupTransactionData.getGroupId()));

			Serialization.serializeBigDecimal(bytes, joinGroupTransactionData.getFee());

			if (joinGroupTransactionData.getSignature() != null)
				bytes.write(joinGroupTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			JoinGroupTransactionData joinGroupTransactionData = (JoinGroupTransactionData) transactionData;

			byte[] joinerPublicKey = joinGroupTransactionData.getJoinerPublicKey();

			json.put("joiner", PublicKeyAccount.getAddress(joinerPublicKey));
			json.put("joinerPublicKey", HashCode.fromBytes(joinerPublicKey).toString());

			json.put("groupId", joinGroupTransactionData.getGroupId());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
