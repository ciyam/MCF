package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.GroupUnbanTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class GroupUnbanTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ADMIN_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + ADMIN_LENGTH + NAME_SIZE_LENGTH + MEMBER_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] adminPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String groupName = Serialization.deserializeSizedString(byteBuffer, Group.MAX_NAME_SIZE);

		String member = Serialization.deserializeAddress(byteBuffer);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new GroupUnbanTransactionData(adminPublicKey, groupName, member, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		GroupUnbanTransactionData groupUnbanTransactionData = (GroupUnbanTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(groupUnbanTransactionData.getGroupName());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GroupUnbanTransactionData groupUnbanTransactionData = (GroupUnbanTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(groupUnbanTransactionData.getType().value));
			bytes.write(Longs.toByteArray(groupUnbanTransactionData.getTimestamp()));
			bytes.write(groupUnbanTransactionData.getReference());

			bytes.write(groupUnbanTransactionData.getCreatorPublicKey());
			Serialization.serializeSizedString(bytes, groupUnbanTransactionData.getGroupName());
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
			GroupUnbanTransactionData groupUnbanTransactionData = (GroupUnbanTransactionData) transactionData;

			byte[] adminPublicKey = groupUnbanTransactionData.getAdminPublicKey();

			json.put("admin", PublicKeyAccount.getAddress(adminPublicKey));
			json.put("adminPublicKey", HashCode.fromBytes(adminPublicKey).toString());

			json.put("groupName", groupUnbanTransactionData.getGroupName());
			json.put("member", groupUnbanTransactionData.getMember());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
