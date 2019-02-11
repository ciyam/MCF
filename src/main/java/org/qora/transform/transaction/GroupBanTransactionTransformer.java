package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.GroupBanTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class GroupBanTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ADMIN_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int GROUPID_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;
	private static final int REASON_SIZE_LENGTH = INT_LENGTH;
	private static final int TTL_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + ADMIN_LENGTH + GROUPID_LENGTH + MEMBER_LENGTH + REASON_SIZE_LENGTH + TTL_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.GROUP_BAN.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group admin's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
		layout.add("account to ban", TransformationType.ADDRESS);
		layout.add("ban reason length", TransformationType.INT);
		layout.add("ban reason", TransformationType.STRING);
		layout.add("ban period (seconds) or 0 forever", TransformationType.INT);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] adminPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		String offender = Serialization.deserializeAddress(byteBuffer);

		String reason = Serialization.deserializeSizedString(byteBuffer, Group.MAX_REASON_SIZE);

		int timeToLive = byteBuffer.getInt();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new GroupBanTransactionData(adminPublicKey, groupId, offender, reason, timeToLive, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		GroupBanTransactionData groupBanTransactionData = (GroupBanTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(groupBanTransactionData.getReason());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GroupBanTransactionData groupBanTransactionData = (GroupBanTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(groupBanTransactionData.getType().value));
			bytes.write(Longs.toByteArray(groupBanTransactionData.getTimestamp()));
			bytes.write(groupBanTransactionData.getReference());

			bytes.write(groupBanTransactionData.getCreatorPublicKey());
			bytes.write(Ints.toByteArray(groupBanTransactionData.getGroupId()));
			Serialization.serializeAddress(bytes, groupBanTransactionData.getOffender());
			Serialization.serializeSizedString(bytes, groupBanTransactionData.getReason());
			bytes.write(Ints.toByteArray(groupBanTransactionData.getTimeToLive()));

			Serialization.serializeBigDecimal(bytes, groupBanTransactionData.getFee());

			if (groupBanTransactionData.getSignature() != null)
				bytes.write(groupBanTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			GroupBanTransactionData groupBanTransactionData = (GroupBanTransactionData) transactionData;

			byte[] adminPublicKey = groupBanTransactionData.getAdminPublicKey();

			json.put("admin", PublicKeyAccount.getAddress(adminPublicKey));
			json.put("adminPublicKey", HashCode.fromBytes(adminPublicKey).toString());

			json.put("groupId", groupBanTransactionData.getGroupId());
			json.put("offender", groupBanTransactionData.getOffender());
			json.put("reason", groupBanTransactionData.getReason());
			json.put("timeToLive", groupBanTransactionData.getTimeToLive());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
