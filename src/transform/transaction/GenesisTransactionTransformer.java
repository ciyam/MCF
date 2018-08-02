package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.TransactionData;
import data.transaction.GenesisTransactionData;
import transform.TransformationException;
import utils.Serialization;

public class GenesisTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int AMOUNT_LENGTH = LONG_LENGTH;

	// Note that Genesis transactions don't require reference, fee or signature:
	private static final int TYPELESS_LENGTH = TIMESTAMP_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		String recipient = Serialization.deserializeAddress(byteBuffer);

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		return new GenesisTransactionData(recipient, amount, timestamp);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GenesisTransactionData genesisTransactionData = (GenesisTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(genesisTransactionData.getType().value));
			bytes.write(Longs.toByteArray(genesisTransactionData.getTimestamp()));

			Serialization.serializeAddress(bytes, genesisTransactionData.getRecipient());
			Serialization.serializeBigDecimal(bytes, genesisTransactionData.getAmount());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			GenesisTransactionData genesisTransactionData = (GenesisTransactionData) transactionData;

			json.put("recipient", genesisTransactionData.getRecipient());
			json.put("amount", genesisTransactionData.getAmount().toPlainString());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
