package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.Transaction;
import data.account.Account;
import data.transaction.GenesisTransaction;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class GenesisTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int AMOUNT_LENGTH = LONG_LENGTH;
	// Note that Genesis transactions don't require reference, fee or signature:
	private static final int TYPELESS_LENGTH = TIMESTAMP_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;

	static Transaction fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new TransformationException("Byte data too short for GenesisTransaction");

		long timestamp = byteBuffer.getLong();
		Account recipient = new Account(Serialization.deserializeRecipient(byteBuffer));
		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		return new GenesisTransaction(recipient, amount, timestamp);
	}

	public static int getDataLength(Transaction baseTransaction) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(Transaction baseTransaction) throws TransformationException {
		try {
			GenesisTransaction transaction = (GenesisTransaction) baseTransaction;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(transaction.getType().value));
			bytes.write(Longs.toByteArray(transaction.getTimestamp()));
			bytes.write(Base58.decode(transaction.getRecipient().getAddress()));
			bytes.write(Serialization.serializeBigDecimal(transaction.getAmount()));

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(Transaction baseTransaction) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(baseTransaction);

		try {
			GenesisTransaction transaction = (GenesisTransaction) baseTransaction;

			json.put("recipient", transaction.getRecipient().getAddress());
			json.put("amount", transaction.getAmount().toPlainString());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
