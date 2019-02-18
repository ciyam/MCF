package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qora.block.BlockChain;
import org.qora.data.PaymentData;
import org.qora.data.transaction.MultiPaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.PaymentTransformer;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.primitives.Ints;

public class MultiPaymentTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int PAYMENTS_COUNT_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = PAYMENTS_COUNT_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.MULTI_PAYMENT.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("sender's public key", TransformationType.PUBLIC_KEY);
		layout.add("number of payments", TransformationType.INT);
		layout.add("* recipient", TransformationType.ADDRESS);
		layout.add("* asset ID of payment", TransformationType.LONG);
		layout.add("* payment amount", TransformationType.AMOUNT);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = 0;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int paymentsCount = byteBuffer.getInt();

		List<PaymentData> payments = new ArrayList<PaymentData>();
		for (int i = 0; i < paymentsCount; ++i)
			payments.add(PaymentTransformer.fromByteBuffer(byteBuffer));

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new MultiPaymentTransactionData(timestamp, txGroupId, reference, senderPublicKey, payments, fee, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + multiPaymentTransactionData.getPayments().size() * PaymentTransformer.getDataLength();
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			List<PaymentData> payments = multiPaymentTransactionData.getPayments();
			bytes.write(Ints.toByteArray(payments.size()));

			for (PaymentData paymentData : payments)
				bytes.write(PaymentTransformer.toBytes(paymentData));

			Serialization.serializeBigDecimal(bytes, multiPaymentTransactionData.getFee());

			if (multiPaymentTransactionData.getSignature() != null)
				bytes.write(multiPaymentTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	/**
	 * In Qora v1, the bytes used for verification are really mangled so we need to test for v1-ness and adjust the bytes accordingly.
	 * 
	 * @param transactionData
	 * @return byte[]
	 * @throws TransformationException
	 */
	public static byte[] toBytesForSigningImpl(TransactionData transactionData) throws TransformationException {
		byte[] bytes = TransactionTransformer.toBytesForSigningImpl(transactionData);

		if (transactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
			return bytes;

		// Special v1 version

		// In v1, a coding error means that all bytes prior to final payment entry are lost!
		// So we're left with: final payment entry and fee. Signature has already been stripped
		int v1Length = PaymentTransformer.getDataLength() + FEE_LENGTH;
		int v1Start = bytes.length - v1Length;

		return Arrays.copyOfRange(bytes, v1Start, bytes.length);
	}

}
