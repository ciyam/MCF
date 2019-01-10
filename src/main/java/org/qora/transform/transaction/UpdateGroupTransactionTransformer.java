package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.UpdateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class UpdateGroupTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NEW_OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int NEW_DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int NEW_IS_OPEN_LENGTH = BOOLEAN_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + OWNER_LENGTH + NEW_OWNER_LENGTH + NAME_SIZE_LENGTH + NEW_DESCRIPTION_SIZE_LENGTH + NEW_IS_OPEN_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String groupName = Serialization.deserializeSizedString(byteBuffer, Group.MAX_NAME_SIZE);

		String newOwner = Serialization.deserializeAddress(byteBuffer);

		String newDescription = Serialization.deserializeSizedString(byteBuffer, Group.MAX_DESCRIPTION_SIZE);

		boolean newIsOpen = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new UpdateGroupTransactionData(ownerPublicKey, groupName, newOwner, newDescription, newIsOpen, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		UpdateGroupTransactionData updateGroupTransactionData = (UpdateGroupTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(updateGroupTransactionData.getGroupName())
				+ Utf8.encodedLength(updateGroupTransactionData.getNewDescription());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			UpdateGroupTransactionData updateGroupTransactionData = (UpdateGroupTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(updateGroupTransactionData.getType().value));
			bytes.write(Longs.toByteArray(updateGroupTransactionData.getTimestamp()));
			bytes.write(updateGroupTransactionData.getReference());

			bytes.write(updateGroupTransactionData.getCreatorPublicKey());
			Serialization.serializeSizedString(bytes, updateGroupTransactionData.getGroupName());

			Serialization.serializeAddress(bytes, updateGroupTransactionData.getNewOwner());
			Serialization.serializeSizedString(bytes, updateGroupTransactionData.getNewDescription());

			bytes.write((byte) (updateGroupTransactionData.getNewIsOpen() ? 1 : 0));

			Serialization.serializeBigDecimal(bytes, updateGroupTransactionData.getFee());

			if (updateGroupTransactionData.getSignature() != null)
				bytes.write(updateGroupTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			UpdateGroupTransactionData updateGroupTransactionData = (UpdateGroupTransactionData) transactionData;

			byte[] ownerPublicKey = updateGroupTransactionData.getOwnerPublicKey();

			json.put("owner", PublicKeyAccount.getAddress(ownerPublicKey));
			json.put("ownerPublicKey", HashCode.fromBytes(ownerPublicKey).toString());

			json.put("groupName", updateGroupTransactionData.getGroupName());
			json.put("newOwner", updateGroupTransactionData.getNewOwner());
			json.put("newDescription", updateGroupTransactionData.getNewDescription());
			json.put("newIsOpen", updateGroupTransactionData.getNewIsOpen());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
