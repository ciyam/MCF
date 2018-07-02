package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.SellNameTransactionData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.naming.Name;
import transform.TransformationException;
import utils.Serialization;

public class SellNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int AMOUNT_LENGTH = BIG_DECIMAL_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + AMOUNT_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_DATALESS_LENGTH)
			throw new TransformationException("Byte data too short for SellNameTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);

		// Still need to make sure there are enough bytes left for remaining fields
		if (byteBuffer.remaining() < AMOUNT_LENGTH + FEE_LENGTH + SIGNATURE_LENGTH)
			throw new TransformationException("Byte data too short for SellNameTransaction");

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new SellNameTransactionData(ownerPublicKey, name, amount, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		SellNameTransactionData sellNameTransactionData = (SellNameTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + sellNameTransactionData.getName().length();

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			SellNameTransactionData sellNameTransactionData = (SellNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(sellNameTransactionData.getType().value));
			bytes.write(Longs.toByteArray(sellNameTransactionData.getTimestamp()));
			bytes.write(sellNameTransactionData.getReference());

			bytes.write(sellNameTransactionData.getOwnerPublicKey());
			Serialization.serializeSizedString(bytes, sellNameTransactionData.getName());
			Serialization.serializeBigDecimal(bytes, sellNameTransactionData.getAmount());

			Serialization.serializeBigDecimal(bytes, sellNameTransactionData.getFee());

			if (sellNameTransactionData.getSignature() != null)
				bytes.write(sellNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			SellNameTransactionData sellNameTransactionData = (SellNameTransactionData) transactionData;

			byte[] ownerPublicKey = sellNameTransactionData.getOwnerPublicKey();

			json.put("owner", PublicKeyAccount.getAddress(ownerPublicKey));
			json.put("ownerPublicKey", HashCode.fromBytes(ownerPublicKey).toString());

			json.put("name", sellNameTransactionData.getName());
			json.put("amount", sellNameTransactionData.getAmount().toPlainString());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
