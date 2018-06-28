package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import data.PaymentData;
import data.transaction.MultiPaymentTransactionData;
import transform.PaymentTransformer;
import transform.TransformationException;
import utils.Serialization;

public class MultiPaymentTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int SENDER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int PAYMENTS_COUNT_LENGTH = INT_LENGTH;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + SENDER_LENGTH + PAYMENTS_COUNT_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new TransformationException("Byte data too short for PaymentTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);
		int paymentsCount = byteBuffer.getInt();

		// Check remaining buffer size
		int minRemaining = paymentsCount * PaymentTransformer.getDataLength() + FEE_LENGTH + SIGNATURE_LENGTH;
		if (byteBuffer.remaining() < minRemaining)
			throw new TransformationException("Byte data too short for PaymentTransaction");

		List<PaymentData> payments = new ArrayList<PaymentData>();
		for (int i = 0; i < paymentsCount; ++i)
			payments.add(PaymentTransformer.fromByteBuffer(byteBuffer));

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new MultiPaymentTransactionData(senderPublicKey, payments, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

		return TYPE_LENGTH + TYPELESS_LENGTH + multiPaymentTransactionData.getPayments().size() * PaymentTransformer.getDataLength();
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(multiPaymentTransactionData.getType().value));
			bytes.write(Longs.toByteArray(multiPaymentTransactionData.getTimestamp()));
			bytes.write(multiPaymentTransactionData.getReference());

			bytes.write(multiPaymentTransactionData.getSenderPublicKey());

			List<PaymentData> payments = multiPaymentTransactionData.getPayments();
			bytes.write(Ints.toByteArray(payments.size()));

			for (PaymentData paymentData : payments)
				PaymentTransformer.toBytes(paymentData);

			Serialization.serializeBigDecimal(bytes, multiPaymentTransactionData.getFee());

			if (multiPaymentTransactionData.getSignature() != null)
				bytes.write(multiPaymentTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

			byte[] senderPublicKey = multiPaymentTransactionData.getSenderPublicKey();

			json.put("sender", PublicKeyAccount.getAddress(senderPublicKey));
			json.put("senderPublicKey", HashCode.fromBytes(senderPublicKey).toString());

			List<PaymentData> payments = multiPaymentTransactionData.getPayments();
			JSONArray paymentsJson = new JSONArray();

			for (PaymentData paymentData : payments)
				paymentsJson.add(PaymentTransformer.toJSON(paymentData));

			json.put("payments", paymentsJson);
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
