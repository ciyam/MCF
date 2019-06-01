package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qora.block.BlockChain;
import org.qora.crypto.Crypto;
import org.qora.data.PaymentData;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.ArbitraryTransactionData.DataType;
import org.qora.transaction.ArbitraryTransaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.PaymentTransformer;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.primitives.Ints;

public class ArbitraryTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int SERVICE_LENGTH = INT_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = SERVICE_LENGTH + DATA_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.ARBITRARY.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("sender's public key", TransformationType.PUBLIC_KEY);
		layout.add("number of payments", TransformationType.INT);

		layout.add("* recipient", TransformationType.ADDRESS);
		layout.add("* asset ID of payment", TransformationType.LONG);
		layout.add("* payment amount", TransformationType.AMOUNT);

		layout.add("service ID", TransformationType.INT);
		layout.add("data length", TransformationType.INT);
		layout.add("data", TransformationType.DATA);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int version = ArbitraryTransaction.getVersionByTimestamp(timestamp);

		int txGroupId = 0;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);

		// V3+ allows payments but always return a list of payments, even if empty
		List<PaymentData> payments = new ArrayList<PaymentData>();
		if (version != 1) {
			int paymentsCount = byteBuffer.getInt();

			for (int i = 0; i < paymentsCount; ++i)
				payments.add(PaymentTransformer.fromByteBuffer(byteBuffer));
		}

		int service = byteBuffer.getInt();

		int dataSize = byteBuffer.getInt();
		// Don't allow invalid dataSize here to avoid run-time issues
		if (dataSize > ArbitraryTransaction.MAX_DATA_SIZE)
			throw new TransformationException("ArbitraryTransaction data size too large");

		byte[] data = new byte[dataSize];
		byteBuffer.get(data);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, senderPublicKey, fee, signature);

		return new ArbitraryTransactionData(baseTransactionData, version, service, data, DataType.RAW_DATA, payments);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

		int length = getBaseLength(transactionData) + EXTRAS_LENGTH;

		// V3+ transactions have optional payments
		if (arbitraryTransactionData.getVersion() >= 3)
			length += arbitraryTransactionData.getData().length + arbitraryTransactionData.getPayments().size() * PaymentTransformer.getDataLength();

		return length;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			if (arbitraryTransactionData.getVersion() != 1) {
				List<PaymentData> payments = arbitraryTransactionData.getPayments();
				bytes.write(Ints.toByteArray(payments.size()));

				for (PaymentData paymentData : payments)
					bytes.write(PaymentTransformer.toBytes(paymentData));
			}

			bytes.write(Ints.toByteArray(arbitraryTransactionData.getService()));

			bytes.write(Ints.toByteArray(arbitraryTransactionData.getData().length));
			bytes.write(arbitraryTransactionData.getData());

			Serialization.serializeBigDecimal(bytes, arbitraryTransactionData.getFee());

			if (arbitraryTransactionData.getSignature() != null)
				bytes.write(arbitraryTransactionData.getSignature());

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
		ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

		// For v4, signature uses hash of data, not raw data itself
		if (arbitraryTransactionData.getVersion() == 4)
			return toBytesForSigningImplV4(arbitraryTransactionData);

		byte[] bytes = TransactionTransformer.toBytesForSigningImpl(transactionData);
		if (arbitraryTransactionData.getVersion() == 1 || transactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
			return bytes;

		// Special v1 version

		// In v1, a coding error means that all bytes prior to final payment entry are lost!
		// If there are no payments then we can skip mangling
		if (arbitraryTransactionData.getPayments().size() == 0)
			return bytes;

		// So we're left with: final payment entry, service, data size, data, fee
		int v1Length = PaymentTransformer.getDataLength() + SERVICE_LENGTH + DATA_SIZE_LENGTH + arbitraryTransactionData.getData().length + FEE_LENGTH;
		int v1Start = bytes.length - v1Length;

		return Arrays.copyOfRange(bytes, v1Start, bytes.length);
	}

	private static byte[] toBytesForSigningImplV4(ArbitraryTransactionData arbitraryTransactionData) throws TransformationException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(arbitraryTransactionData, bytes);

			if (arbitraryTransactionData.getVersion() != 1) {
				List<PaymentData> payments = arbitraryTransactionData.getPayments();
				bytes.write(Ints.toByteArray(payments.size()));

				for (PaymentData paymentData : payments)
					bytes.write(PaymentTransformer.toBytes(paymentData));
			}

			bytes.write(Ints.toByteArray(arbitraryTransactionData.getService()));

			switch (arbitraryTransactionData.getDataType()) {
				case DATA_HASH:
					bytes.write(arbitraryTransactionData.getData());
					break;

				case RAW_DATA:
					bytes.write(Crypto.digest(arbitraryTransactionData.getData()));
					break;
			}

			Serialization.serializeBigDecimal(bytes, arbitraryTransactionData.getFee());

			// Never append signature

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}

	}

}
