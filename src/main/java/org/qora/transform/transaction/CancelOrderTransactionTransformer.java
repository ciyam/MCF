package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.CancelOrderTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transform.TransformationException;
import org.qora.utils.Base58;
import org.qora.utils.Serialization;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class CancelOrderTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int CREATOR_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int ORDER_ID_LENGTH = SIGNATURE_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + CREATOR_LENGTH + ORDER_ID_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		byte[] orderId = new byte[ORDER_ID_LENGTH];
		byteBuffer.get(orderId);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new CancelOrderTransactionData(creatorPublicKey, orderId, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CancelOrderTransactionData cancelOrderTransactionData = (CancelOrderTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(cancelOrderTransactionData.getType().value));
			bytes.write(Longs.toByteArray(cancelOrderTransactionData.getTimestamp()));
			bytes.write(cancelOrderTransactionData.getReference());

			bytes.write(cancelOrderTransactionData.getCreatorPublicKey());
			bytes.write(cancelOrderTransactionData.getOrderId());

			Serialization.serializeBigDecimal(bytes, cancelOrderTransactionData.getFee());

			if (cancelOrderTransactionData.getSignature() != null)
				bytes.write(cancelOrderTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			CancelOrderTransactionData cancelOrderTransactionData = (CancelOrderTransactionData) transactionData;

			byte[] creatorPublicKey = cancelOrderTransactionData.getCreatorPublicKey();

			json.put("creator", PublicKeyAccount.getAddress(creatorPublicKey));
			json.put("creatorPublicKey", HashCode.fromBytes(creatorPublicKey).toString());

			json.put("order", Base58.encode(cancelOrderTransactionData.getOrderId()));
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
