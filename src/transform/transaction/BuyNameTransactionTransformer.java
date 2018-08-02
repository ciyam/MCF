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

import data.transaction.BuyNameTransactionData;
import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.naming.Name;
import transform.TransformationException;
import utils.Serialization;

public class BuyNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int BUYER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int AMOUNT_LENGTH = BIG_DECIMAL_LENGTH;
	private static final int SELLER_LENGTH = ADDRESS_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + BUYER_LENGTH + NAME_SIZE_LENGTH + AMOUNT_LENGTH + SELLER_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] buyerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		String seller = Serialization.deserializeAddress(byteBuffer);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new BuyNameTransactionData(buyerPublicKey, name, amount, seller, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) transactionData;

		int dataLength = TYPE_LENGTH + TYPELESS_DATALESS_LENGTH + Utf8.encodedLength(buyNameTransactionData.getName());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(buyNameTransactionData.getType().value));
			bytes.write(Longs.toByteArray(buyNameTransactionData.getTimestamp()));
			bytes.write(buyNameTransactionData.getReference());

			bytes.write(buyNameTransactionData.getBuyerPublicKey());
			Serialization.serializeSizedString(bytes, buyNameTransactionData.getName());
			Serialization.serializeBigDecimal(bytes, buyNameTransactionData.getAmount());
			Serialization.serializeAddress(bytes, buyNameTransactionData.getSeller());

			Serialization.serializeBigDecimal(bytes, buyNameTransactionData.getFee());

			if (buyNameTransactionData.getSignature() != null)
				bytes.write(buyNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) transactionData;

			byte[] buyerPublicKey = buyNameTransactionData.getBuyerPublicKey();

			json.put("buyer", PublicKeyAccount.getAddress(buyerPublicKey));
			json.put("buyerPublicKey", HashCode.fromBytes(buyerPublicKey).toString());

			json.put("name", buyNameTransactionData.getName());
			json.put("amount", buyNameTransactionData.getAmount().toPlainString());

			json.put("seller", buyNameTransactionData.getSeller());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
