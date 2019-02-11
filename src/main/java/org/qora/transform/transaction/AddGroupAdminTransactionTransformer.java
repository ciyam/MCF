package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.AddGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class AddGroupAdminTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int GROUPID_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + OWNER_LENGTH + GROUPID_LENGTH + MEMBER_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.ADD_GROUP_ADMIN.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
		layout.add("member to promote to admin", TransformationType.ADDRESS);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		String member = Serialization.deserializeAddress(byteBuffer);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new AddGroupAdminTransactionData(ownerPublicKey, groupId, member, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			AddGroupAdminTransactionData addGroupAdminTransactionData = (AddGroupAdminTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(addGroupAdminTransactionData.getType().value));
			bytes.write(Longs.toByteArray(addGroupAdminTransactionData.getTimestamp()));
			bytes.write(addGroupAdminTransactionData.getReference());

			bytes.write(addGroupAdminTransactionData.getCreatorPublicKey());
			bytes.write(Ints.toByteArray(addGroupAdminTransactionData.getGroupId()));
			Serialization.serializeAddress(bytes, addGroupAdminTransactionData.getMember());

			Serialization.serializeBigDecimal(bytes, addGroupAdminTransactionData.getFee());

			if (addGroupAdminTransactionData.getSignature() != null)
				bytes.write(addGroupAdminTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			AddGroupAdminTransactionData addGroupAdminTransactionData = (AddGroupAdminTransactionData) transactionData;

			byte[] ownerPublicKey = addGroupAdminTransactionData.getOwnerPublicKey();

			json.put("owner", PublicKeyAccount.getAddress(ownerPublicKey));
			json.put("ownerPublicKey", HashCode.fromBytes(ownerPublicKey).toString());

			json.put("groupId", addGroupAdminTransactionData.getGroupId());
			json.put("member", addGroupAdminTransactionData.getMember());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
