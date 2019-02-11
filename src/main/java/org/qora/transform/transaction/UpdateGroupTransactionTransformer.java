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
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class UpdateGroupTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int GROUPID_LENGTH = INT_LENGTH;
	private static final int NEW_OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NEW_DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int NEW_IS_OPEN_LENGTH = BOOLEAN_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + OWNER_LENGTH + GROUPID_LENGTH + NEW_OWNER_LENGTH + NEW_DESCRIPTION_SIZE_LENGTH
			+ NEW_IS_OPEN_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.UPDATE_GROUP.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
		layout.add("group's new owner", TransformationType.ADDRESS);
		layout.add("group's new description length", TransformationType.INT);
		layout.add("group's new description", TransformationType.STRING);
		layout.add("is group \"open\"?", TransformationType.BOOLEAN);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		String newOwner = Serialization.deserializeAddress(byteBuffer);

		String newDescription = Serialization.deserializeSizedString(byteBuffer, Group.MAX_DESCRIPTION_SIZE);

		boolean newIsOpen = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new UpdateGroupTransactionData(ownerPublicKey, groupId, newOwner, newDescription, newIsOpen, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		UpdateGroupTransactionData updateGroupTransactionData = (UpdateGroupTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(updateGroupTransactionData.getNewDescription());

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
			bytes.write(Ints.toByteArray(updateGroupTransactionData.getGroupId()));

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

			json.put("groupId", updateGroupTransactionData.getGroupId());
			json.put("newOwner", updateGroupTransactionData.getNewOwner());
			json.put("newDescription", updateGroupTransactionData.getNewDescription());
			json.put("newIsOpen", updateGroupTransactionData.getNewIsOpen());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
