package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.RemoveGroupAdminTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class RemoveGroupAdminTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int GROUPID_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + OWNER_LENGTH + GROUPID_LENGTH + MEMBER_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.REMOVE_GROUP_ADMIN.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
		layout.add("admin to demote", TransformationType.ADDRESS);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		String admin = Serialization.deserializeAddress(byteBuffer);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new RemoveGroupAdminTransactionData(ownerPublicKey, groupId, admin, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			RemoveGroupAdminTransactionData removeGroupAdminTransactionData = (RemoveGroupAdminTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(removeGroupAdminTransactionData.getType().value));
			bytes.write(Longs.toByteArray(removeGroupAdminTransactionData.getTimestamp()));
			bytes.write(removeGroupAdminTransactionData.getReference());

			bytes.write(removeGroupAdminTransactionData.getCreatorPublicKey());
			bytes.write(Ints.toByteArray(removeGroupAdminTransactionData.getGroupId()));
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

			json.put("groupId", removeGroupAdminTransactionData.getGroupId());
			json.put("admin", removeGroupAdminTransactionData.getAdmin());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
