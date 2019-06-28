package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.BuyNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.naming.Name;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class BuyNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int BUYER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int AMOUNT_LENGTH = BIG_DECIMAL_LENGTH;
	private static final int SELLER_LENGTH = ADDRESS_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH = BASE_TYPELESS_LENGTH + BUYER_LENGTH + NAME_SIZE_LENGTH + AMOUNT_LENGTH + SELLER_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.BUY_NAME.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("buyer's public key", TransformationType.PUBLIC_KEY);
		layout.add("name length", TransformationType.INT);
		layout.add("name", TransformationType.STRING);
		layout.add("buy price", TransformationType.AMOUNT);
		layout.add("seller", TransformationType.ADDRESS);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
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
