package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.CreateGroupTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class CreateGroupTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int CREATOR_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int IS_OPEN_LENGTH = BOOLEAN_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + CREATOR_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH
			+ IS_OPEN_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.CREATE_GROUP.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group creator's public key", TransformationType.PUBLIC_KEY);
		layout.add("group's name length", TransformationType.INT);
		layout.add("group's name", TransformationType.STRING);
		layout.add("group's description length", TransformationType.INT);
		layout.add("group's description", TransformationType.STRING);
		layout.add("is group \"open\"?", TransformationType.BOOLEAN);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String owner = Serialization.deserializeAddress(byteBuffer);

		String groupName = Serialization.deserializeSizedString(byteBuffer, Group.MAX_NAME_SIZE);

		String description = Serialization.deserializeSizedString(byteBuffer, Group.MAX_DESCRIPTION_SIZE);

		boolean isOpen = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new CreateGroupTransactionData(creatorPublicKey, owner, groupName, description, isOpen, null, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		CreateGroupTransactionData createGroupTransactionData = (CreateGroupTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(createGroupTransactionData.getGroupName())
				+ Utf8.encodedLength(createGroupTransactionData.getDescription());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CreateGroupTransactionData createGroupTransactionData = (CreateGroupTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(createGroupTransactionData.getType().value));
			bytes.write(Longs.toByteArray(createGroupTransactionData.getTimestamp()));
			bytes.write(createGroupTransactionData.getReference());

			bytes.write(createGroupTransactionData.getCreatorPublicKey());
			Serialization.serializeAddress(bytes, createGroupTransactionData.getOwner());
			Serialization.serializeSizedString(bytes, createGroupTransactionData.getGroupName());
			Serialization.serializeSizedString(bytes, createGroupTransactionData.getDescription());

			bytes.write((byte) (createGroupTransactionData.getIsOpen() ? 1 : 0));

			Serialization.serializeBigDecimal(bytes, createGroupTransactionData.getFee());

			if (createGroupTransactionData.getSignature() != null)
				bytes.write(createGroupTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			CreateGroupTransactionData createGroupTransactionData = (CreateGroupTransactionData) transactionData;

			byte[] creatorPublicKey = createGroupTransactionData.getCreatorPublicKey();

			json.put("creator", PublicKeyAccount.getAddress(creatorPublicKey));
			json.put("creatorPublicKey", HashCode.fromBytes(creatorPublicKey).toString());

			json.put("owner", createGroupTransactionData.getOwner());
			json.put("groupName", createGroupTransactionData.getGroupName());
			json.put("description", createGroupTransactionData.getDescription());
			json.put("isOpen", createGroupTransactionData.getIsOpen());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
