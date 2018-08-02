package transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import data.transaction.TransactionData;
import data.transaction.TransferAssetTransactionData;
import qora.account.PublicKeyAccount;
import transform.TransformationException;
import utils.Serialization;

public class TransferAssetTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int SENDER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int AMOUNT_LENGTH = 12;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + ASSET_ID_LENGTH + AMOUNT_LENGTH;

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String recipient = Serialization.deserializeAddress(byteBuffer);

		long assetId = byteBuffer.getLong();

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer, AMOUNT_LENGTH);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new TransferAssetTransactionData(senderPublicKey, recipient, amount, assetId, fee, timestamp, reference, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(transferAssetTransactionData.getType().value));
			bytes.write(Longs.toByteArray(transferAssetTransactionData.getTimestamp()));
			bytes.write(transferAssetTransactionData.getReference());

			bytes.write(transferAssetTransactionData.getSenderPublicKey());
			Serialization.serializeAddress(bytes, transferAssetTransactionData.getRecipient());
			bytes.write(Longs.toByteArray(transferAssetTransactionData.getAssetId()));
			Serialization.serializeBigDecimal(bytes, transferAssetTransactionData.getAmount(), AMOUNT_LENGTH);

			Serialization.serializeBigDecimal(bytes, transferAssetTransactionData.getFee());

			if (transferAssetTransactionData.getSignature() != null)
				bytes.write(transferAssetTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		JSONObject json = TransactionTransformer.getBaseJSON(transactionData);

		try {
			TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) transactionData;

			byte[] senderPublicKey = transferAssetTransactionData.getSenderPublicKey();

			json.put("sender", PublicKeyAccount.getAddress(senderPublicKey));
			json.put("senderPublicKey", HashCode.fromBytes(senderPublicKey).toString());
			json.put("recipient", transferAssetTransactionData.getRecipient());

			// For gen1 support:
			json.put("asset", transferAssetTransactionData.getAssetId());
			// Gen2 version:
			json.put("assetId", transferAssetTransactionData.getAssetId());

			json.put("amount", transferAssetTransactionData.getAmount().toPlainString());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
