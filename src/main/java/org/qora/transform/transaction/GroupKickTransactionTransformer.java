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
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class GroupKickTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ADMIN_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int GROUPID_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;
	private static final int REASON_SIZE_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + ADMIN_LENGTH + GROUPID_LENGTH + MEMBER_LENGTH + REASON_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.GROUP_KICK.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group admin's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
		layout.add("group member to kick", TransformationType.ADDRESS);
		layout.add("kick reason length", TransformationType.INT);
		layout.add("kick reason", TransformationType.STRING);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] adminPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		String member = Serialization.deserializeAddress(byteBuffer);

		String reason = Serialization.deserializeSizedString(byteBuffer, Group.MAX_REASON_SIZE);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new GroupKickTransactionData(adminPublicKey, groupId, member, reason, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		GroupKickTransactionData groupKickTransactionData = (GroupKickTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(groupKickTransactionData.getReason());

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
			bytes.write(Ints.toByteArray(groupKickTransactionData.getGroupId()));
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

			json.put("groupId", groupKickTransactionData.getGroupId());
			json.put("member", groupKickTransactionData.getMember());
			json.put("reason", groupKickTransactionData.getReason());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
