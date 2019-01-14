package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.GroupKickTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class GroupKickTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ADMIN_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;
	private static final int REASON_SIZE_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + ADMIN_LENGTH + NAME_SIZE_LENGTH + MEMBER_LENGTH + REASON_SIZE_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] adminPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String groupName = Serialization.deserializeSizedString(byteBuffer, Group.MAX_NAME_SIZE);

		String member = Serialization.deserializeAddress(byteBuffer);

		String reason = Serialization.deserializeSizedString(byteBuffer, Group.MAX_REASON_SIZE);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new GroupKickTransactionData(adminPublicKey, groupName, member, reason, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		GroupKickTransactionData groupKickTransactionData = (GroupKickTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(groupKickTransactionData.getGroupName())
				+ Utf8.encodedLength(groupKickTransactionData.getReason());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GroupKickTransactionData groupKickTransactionData = (GroupKickTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(groupKickTransactionData.getType().value));
			bytes.write(Longs.toByteArray(groupKickTransactionData.getTimestamp()));
			bytes.write(groupKickTransactionData.getReference());

			bytes.write(groupKickTransactionData.getCreatorPublicKey());
			Serialization.serializeSizedString(bytes, groupKickTransactionData.getGroupName());
			Serialization.serializeAddress(bytes, groupKickTransactionData.getMember());
			Serialization.serializeSizedString(bytes, groupKickTransactionData.getReason());

			Serialization.serializeBigDecimal(bytes, groupKickTransactionData.getFee());

			if (groupKickTransactionData.getSignature() != null)
				bytes.write(groupKickTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			GroupKickTransactionData groupKickTransactionData = (GroupKickTransactionData) transactionData;

			byte[] adminPublicKey = groupKickTransactionData.getAdminPublicKey();

			json.put("admin", PublicKeyAccount.getAddress(adminPublicKey));
			json.put("adminPublicKey", HashCode.fromBytes(adminPublicKey).toString());

			json.put("groupName", groupKickTransactionData.getGroupName());
			json.put("member", groupKickTransactionData.getMember());
			json.put("reason", groupKickTransactionData.getReason());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
