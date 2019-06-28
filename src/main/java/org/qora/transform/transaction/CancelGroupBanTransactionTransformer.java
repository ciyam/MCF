package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.CancelGroupBanTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class CancelGroupBanTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ADMIN_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int GROUPID_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + ADMIN_LENGTH + GROUPID_LENGTH + MEMBER_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] adminPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		String member = Serialization.deserializeAddress(byteBuffer);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new CancelGroupBanTransactionData(adminPublicKey, groupId, member, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CancelGroupBanTransactionData groupUnbanTransactionData = (CancelGroupBanTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(groupUnbanTransactionData.getType().value));
			bytes.write(Longs.toByteArray(groupUnbanTransactionData.getTimestamp()));
			bytes.write(groupUnbanTransactionData.getReference());

			bytes.write(groupUnbanTransactionData.getCreatorPublicKey());
			bytes.write(Ints.toByteArray(groupUnbanTransactionData.getGroupId()));
			Serialization.serializeAddress(bytes, groupUnbanTransactionData.getMember());

			Serialization.serializeBigDecimal(bytes, groupUnbanTransactionData.getFee());

			if (groupUnbanTransactionData.getSignature() != null)
				bytes.write(groupUnbanTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			CancelGroupBanTransactionData groupUnbanTransactionData = (CancelGroupBanTransactionData) transactionData;

			byte[] adminPublicKey = groupUnbanTransactionData.getAdminPublicKey();

			json.put("admin", PublicKeyAccount.getAddress(adminPublicKey));
			json.put("adminPublicKey", HashCode.fromBytes(adminPublicKey).toString());

			json.put("groupId", groupUnbanTransactionData.getGroupId());
			json.put("member", groupUnbanTransactionData.getMember());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
