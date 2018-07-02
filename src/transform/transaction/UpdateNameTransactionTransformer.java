package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.UpdateNameTransactionData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.naming.Name;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class UpdateNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int REGISTRANT_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + REGISTRANT_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + DATA_SIZE_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_DATALESS_LENGTH)
			throw new TransformationException("Byte data too short for UpdateNameTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String newOwner = Serialization.deserializeRecipient(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);
		String newData = Serialization.deserializeSizedString(byteBuffer, Name.MAX_DATA_SIZE);

		// Still need to make sure there are enough bytes left for remaining fields
		if (byteBuffer.remaining() < FEE_LENGTH + SIGNATURE_LENGTH)
			throw new TransformationException("Byte data too short for UpdateNameTransaction");

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new UpdateNameTransactionData(ownerPublicKey, newOwner, name, newData, null, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(updateNameTransactionData.getName())
				+ Utf8.encodedLength(updateNameTransactionData.getNewData());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(updateNameTransactionData.getType().value));
			bytes.write(Longs.toByteArray(updateNameTransactionData.getTimestamp()));
			bytes.write(updateNameTransactionData.getReference());

			bytes.write(updateNameTransactionData.getOwnerPublicKey());
			bytes.write(Base58.decode(updateNameTransactionData.getNewOwner()));
			Serialization.serializeSizedString(bytes, updateNameTransactionData.getName());
			Serialization.serializeSizedString(bytes, updateNameTransactionData.getNewData());

			Serialization.serializeBigDecimal(bytes, updateNameTransactionData.getFee());

			if (updateNameTransactionData.getSignature() != null)
				bytes.write(updateNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

			byte[] ownerPublicKey = updateNameTransactionData.getOwnerPublicKey();

			json.put("owner", PublicKeyAccount.getAddress(ownerPublicKey));
			json.put("ownerPublicKey", HashCode.fromBytes(ownerPublicKey).toString());

			json.put("newOwner", updateNameTransactionData.getNewOwner());
			json.put("name", updateNameTransactionData.getName());
			json.put("newData", updateNameTransactionData.getNewData());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
