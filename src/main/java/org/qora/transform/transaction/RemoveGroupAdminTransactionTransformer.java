package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.RemoveGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class RemoveGroupAdminTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + MEMBER_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String groupName = Serialization.deserializeSizedString(byteBuffer, Group.MAX_NAME_SIZE);

		String admin = Serialization.deserializeAddress(byteBuffer);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new RemoveGroupAdminTransactionData(ownerPublicKey, groupName, admin, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		RemoveGroupAdminTransactionData removeGroupAdminTransactionData = (RemoveGroupAdminTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(removeGroupAdminTransactionData.getGroupName());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			RemoveGroupAdminTransactionData removeGroupAdminTransactionData = (RemoveGroupAdminTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(removeGroupAdminTransactionData.getType().value));
			bytes.write(Longs.toByteArray(removeGroupAdminTransactionData.getTimestamp()));
			bytes.write(removeGroupAdminTransactionData.getReference());

			bytes.write(removeGroupAdminTransactionData.getCreatorPublicKey());
			Serialization.serializeSizedString(bytes, removeGroupAdminTransactionData.getGroupName());
			Serialization.serializeAddress(bytes, removeGroupAdminTransactionData.getAdmin());

			Serialization.serializeBigDecimal(bytes, removeGroupAdminTransactionData.getFee());

			if (removeGroupAdminTransactionData.getSignature() != null)
				bytes.write(removeGroupAdminTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			RemoveGroupAdminTransactionData removeGroupAdminTransactionData = (RemoveGroupAdminTransactionData) transactionData;

			byte[] ownerPublicKey = removeGroupAdminTransactionData.getOwnerPublicKey();

			json.put("owner", PublicKeyAccount.getAddress(ownerPublicKey));
			json.put("ownerPublicKey", HashCode.fromBytes(ownerPublicKey).toString());

			json.put("groupName", removeGroupAdminTransactionData.getGroupName());
			json.put("admin", removeGroupAdminTransactionData.getAdmin());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
