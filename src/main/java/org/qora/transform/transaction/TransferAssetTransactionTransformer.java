package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.TransferAssetTransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class TransferAssetTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int SENDER_LENGTH = PUBLIC_KEY_LENGTH;
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int AMOUNT_LENGTH = 12;

	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + ASSET_ID_LENGTH + AMOUNT_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.TRANSFER_ASSET.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("asset owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("asset's new owner", TransformationType.ADDRESS);
		layout.add("asset ID", TransformationType.LONG);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
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
