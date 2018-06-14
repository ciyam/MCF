package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.TransactionData;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.transaction.MessageTransaction;
import data.transaction.MessageTransactionData;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class MessageTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int SENDER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int AMOUNT_LENGTH = BIG_DECIMAL_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;
	private static final int IS_TEXT_LENGTH = BOOLEAN_LENGTH;
	private static final int IS_ENCRYPTED_LENGTH = BOOLEAN_LENGTH;

	private static final int TYPELESS_DATALESS_LENGTH_V1 = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH + DATA_SIZE_LENGTH
			+ IS_TEXT_LENGTH + IS_ENCRYPTED_LENGTH;
	private static final int TYPELESS_DATALESS_LENGTH_V3 = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + ASSET_ID_LENGTH + AMOUNT_LENGTH
			+ DATA_SIZE_LENGTH + IS_TEXT_LENGTH + IS_ENCRYPTED_LENGTH;

	// Other property lengths
	private static final int MAX_DATA_SIZE = 4000;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_DATALESS_LENGTH_V1)
			throw new TransformationException("Byte data too short for MessageTransaction");

		long timestamp = byteBuffer.getLong();
		int version = MessageTransaction.getVersionByTimestamp(timestamp);

		int minimumRemaining = version == 1 ? TYPELESS_DATALESS_LENGTH_V1 : TYPELESS_DATALESS_LENGTH_V3;
		minimumRemaining -= TIMESTAMP_LENGTH; // Already read above

		if (byteBuffer.remaining() < minimumRemaining)
			throw new TransformationException("Byte data too short for MessageTransaction");

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);
		String recipient = Serialization.deserializeRecipient(byteBuffer);

		long assetId;
		if (version == 1)
			assetId = Asset.QORA;
		else
			assetId = byteBuffer.getLong();

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		int dataSize = byteBuffer.getInt(0);
		// Don't allow invalid dataSize here to avoid run-time issues
		if (dataSize > MAX_DATA_SIZE)
			throw new TransformationException("MessageTransaction data size too large");

		byte[] data = new byte[dataSize];
		byteBuffer.get(data);

		boolean isEncrypted = byteBuffer.get() != 0;
		boolean isText = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new MessageTransactionData(version, senderPublicKey, recipient, assetId, amount, fee, data, isText, isEncrypted, timestamp, reference,
				signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		MessageTransactionData messageTransactionData = (MessageTransactionData) transactionData;

		if (messageTransactionData.getVersion() == 1)
			return TYPE_LENGTH + TYPELESS_DATALESS_LENGTH_V1 + messageTransactionData.getData().length;
		else
			return TYPE_LENGTH + TYPELESS_DATALESS_LENGTH_V3 + messageTransactionData.getData().length;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			MessageTransactionData messageTransactionData = (MessageTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(messageTransactionData.getType().value));
			bytes.write(Longs.toByteArray(messageTransactionData.getTimestamp()));
			bytes.write(messageTransactionData.getReference());

			bytes.write(messageTransactionData.getSenderPublicKey());
			bytes.write(Base58.decode(messageTransactionData.getRecipient()));

			if (messageTransactionData.getVersion() != 1)
				bytes.write(Longs.toByteArray(messageTransactionData.getAssetId()));

			Serialization.serializeBigDecimal(bytes, messageTransactionData.getAmount());
			bytes.write(Ints.toByteArray(messageTransactionData.getData().length));
			bytes.write(messageTransactionData.getData());
			bytes.write((byte) (messageTransactionData.getIsEncrypted() ? 1 : 0));
			bytes.write((byte) (messageTransactionData.getIsText() ? 1 : 0));

			Serialization.serializeBigDecimal(bytes, messageTransactionData.getFee());
			bytes.write(messageTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			MessageTransactionData messageTransactionData = (MessageTransactionData) transactionData;

			byte[] senderPublicKey = messageTransactionData.getSenderPublicKey();

			json.put("version", messageTransactionData.getVersion());
			json.put("sender", PublicKeyAccount.getAddress(senderPublicKey));
			json.put("senderPublicKey", HashCode.fromBytes(senderPublicKey).toString());
			json.put("recipient", messageTransactionData.getRecipient());
			json.put("amount", messageTransactionData.getAmount().toPlainString());
			json.put("assetId", messageTransactionData.getAssetId());
			json.put("isText", messageTransactionData.getIsText());
			json.put("isEncrypted", messageTransactionData.getIsEncrypted());

			// We can only show plain text as unencoded
			if (messageTransactionData.getIsText() && !messageTransactionData.getIsEncrypted())
				json.put("data", new String(messageTransactionData.getData(), Charset.forName("UTF-8")));
			else
				json.put("data", HashCode.fromBytes(messageTransactionData.getData()).toString());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
