package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.block.BlockChain;
import qora.transaction.ArbitraryTransaction;
import data.PaymentData;
import data.transaction.ArbitraryTransactionData;
import data.transaction.ArbitraryTransactionData.DataType;
import transform.PaymentTransformer;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class ArbitraryTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int SENDER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int SERVICE_LENGTH = INT_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;
	private static final int PAYMENTS_COUNT_LENGTH = INT_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH_V1 = BASE_TYPELESS_LENGTH + SENDER_LENGTH + SERVICE_LENGTH + DATA_SIZE_LENGTH;
	private static final int TYPELESS_DATALESS_LENGTH_V3 = BASE_TYPELESS_LENGTH + SENDER_LENGTH + PAYMENTS_COUNT_LENGTH + SERVICE_LENGTH + DATA_SIZE_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int version = ArbitraryTransaction.getVersionByTimestamp(timestamp);

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);

		// V3+ allows payments but always return a list of payments, even if empty
		List<PaymentData> payments = new ArrayList<PaymentData>();
		;
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

		return new ArbitraryTransactionData(version, senderPublicKey, service, data, DataType.RAW_DATA, payments, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

		if (arbitraryTransactionData.getVersion() == 1)
			return TYPE_LENGTH + TYPELESS_DATALESS_LENGTH_V1 + arbitraryTransactionData.getData().length;
		else
			return TYPE_LENGTH + TYPELESS_DATALESS_LENGTH_V3 + arbitraryTransactionData.getData().length
					+ arbitraryTransactionData.getPayments().size() * PaymentTransformer.getDataLength();
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(arbitraryTransactionData.getType().value));
			bytes.write(Longs.toByteArray(arbitraryTransactionData.getTimestamp()));
			bytes.write(arbitraryTransactionData.getReference());

			bytes.write(arbitraryTransactionData.getSenderPublicKey());

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
		byte[] bytes = TransactionTransformer.toBytesForSigningImpl(transactionData);

		if (arbitraryTransactionData.getVersion() == 1 || transactionData.getTimestamp() >= BlockChain.getArbitraryTransactionV2Timestamp())
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

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

			byte[] senderPublicKey = arbitraryTransactionData.getSenderPublicKey();

			json.put("version", arbitraryTransactionData.getVersion());
			json.put("sender", PublicKeyAccount.getAddress(senderPublicKey));
			json.put("senderPublicKey", HashCode.fromBytes(senderPublicKey).toString());

			json.put("service", arbitraryTransactionData.getService());
			json.put("data", Base58.encode(arbitraryTransactionData.getData()));

			if (arbitraryTransactionData.getVersion() != 1) {
				List<PaymentData> payments = arbitraryTransactionData.getPayments();
				JSONArray paymentsJson = new JSONArray();

				for (PaymentData paymentData : payments)
					paymentsJson.add(PaymentTransformer.toJSON(paymentData));

				json.put("payments", paymentsJson);
			}
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
