package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.LeaveGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class LeaveGroupTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int JOINER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int GROUPID_LENGTH = INT_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + JOINER_LENGTH + GROUPID_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] leaverPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new LeaveGroupTransactionData(leaverPublicKey, groupId, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			LeaveGroupTransactionData leaveGroupTransactionData = (LeaveGroupTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(leaveGroupTransactionData.getType().value));
			bytes.write(Longs.toByteArray(leaveGroupTransactionData.getTimestamp()));
			bytes.write(leaveGroupTransactionData.getReference());

			bytes.write(leaveGroupTransactionData.getCreatorPublicKey());
			bytes.write(Ints.toByteArray(leaveGroupTransactionData.getGroupId()));

			Serialization.serializeBigDecimal(bytes, leaveGroupTransactionData.getFee());

			if (leaveGroupTransactionData.getSignature() != null)
				bytes.write(leaveGroupTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			LeaveGroupTransactionData leaveGroupTransactionData = (LeaveGroupTransactionData) transactionData;

			byte[] leaverPublicKey = leaveGroupTransactionData.getLeaverPublicKey();

			json.put("leaver", PublicKeyAccount.getAddress(leaverPublicKey));
			json.put("leaverPublicKey", HashCode.fromBytes(leaverPublicKey).toString());

			json.put("groupId", leaveGroupTransactionData.getGroupId());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
