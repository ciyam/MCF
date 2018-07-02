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

import data.transaction.CancelSellNameTransactionData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.naming.Name;
import transform.TransformationException;
import utils.Serialization;

public class CancelSellNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_DATALESS_LENGTH)
			throw new TransformationException("Byte data too short for CancelSellNameTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);

		// Still need to make sure there are enough bytes left for remaining fields
		if (byteBuffer.remaining() < FEE_LENGTH + SIGNATURE_LENGTH)
			throw new TransformationException("Byte data too short for CancelSellNameTransaction");

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new CancelSellNameTransactionData(ownerPublicKey, name, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		CancelSellNameTransactionData cancelSellNameTransactionData = (CancelSellNameTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(cancelSellNameTransactionData.getName());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CancelSellNameTransactionData cancelSellNameTransactionData = (CancelSellNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(cancelSellNameTransactionData.getType().value));
			bytes.write(Longs.toByteArray(cancelSellNameTransactionData.getTimestamp()));
			bytes.write(cancelSellNameTransactionData.getReference());

			bytes.write(cancelSellNameTransactionData.getOwnerPublicKey());
			Serialization.serializeSizedString(bytes, cancelSellNameTransactionData.getName());

			Serialization.serializeBigDecimal(bytes, cancelSellNameTransactionData.getFee());

			if (cancelSellNameTransactionData.getSignature() != null)
				bytes.write(cancelSellNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			CancelSellNameTransactionData cancelSellNameTransactionData = (CancelSellNameTransactionData) transactionData;

			byte[] ownerPublicKey = cancelSellNameTransactionData.getOwnerPublicKey();

			json.put("owner", PublicKeyAccount.getAddress(ownerPublicKey));
			json.put("ownerPublicKey", HashCode.fromBytes(ownerPublicKey).toString());

			json.put("name", cancelSellNameTransactionData.getName());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
