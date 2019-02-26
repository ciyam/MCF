package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.data.transaction.MessageTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.MessageTransaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class MessageTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int AMOUNT_LENGTH = BIG_DECIMAL_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;
	private static final int IS_TEXT_LENGTH = BOOLEAN_LENGTH;
	private static final int IS_ENCRYPTED_LENGTH = BOOLEAN_LENGTH;

	private static final int EXTRAS_LENGTH = RECIPIENT_LENGTH + AMOUNT_LENGTH + DATA_SIZE_LENGTH + IS_TEXT_LENGTH + IS_ENCRYPTED_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.MESSAGE.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("sender's public key", TransformationType.PUBLIC_KEY);
		layout.add("recipient", TransformationType.ADDRESS);
		layout.add("asset ID of payment", TransformationType.LONG);
		layout.add("payment (can be zero)", TransformationType.AMOUNT);
		layout.add("message length", TransformationType.INT);
		layout.add("message", TransformationType.DATA);
		layout.add("is message encrypted?", TransformationType.BOOLEAN);
		layout.add("is message text?", TransformationType.BOOLEAN);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int version = MessageTransaction.getVersionByTimestamp(timestamp);

		int txGroupId = 0;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String recipient = Serialization.deserializeAddress(byteBuffer);

		long assetId;
		if (version == 1)
			assetId = Asset.QORA;
		else
			assetId = byteBuffer.getLong();

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		int dataSize = byteBuffer.getInt();
		// Don't allow invalid dataSize here to avoid run-time issues
		if (dataSize > MessageTransaction.MAX_DATA_SIZE)
			throw new TransformationException("MessageTransaction data size too large");

		byte[] data = new byte[dataSize];
		byteBuffer.get(data);

		boolean isEncrypted = byteBuffer.get() != 0;

		boolean isText = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new MessageTransactionData(timestamp, txGroupId, reference, senderPublicKey, version, recipient, assetId, amount, data, isText, isEncrypted, fee,
				signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		MessageTransactionData messageTransactionData = (MessageTransactionData) transactionData;

		int dataLength = getBaseLength(transactionData) + EXTRAS_LENGTH;

		// V3+ has assetID for amount
		if (messageTransactionData.getVersion() != 1)
			dataLength += ASSET_ID_LENGTH;

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			MessageTransactionData messageTransactionData = (MessageTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, messageTransactionData.getRecipient());

			if (messageTransactionData.getVersion() != 1)
				bytes.write(Longs.toByteArray(messageTransactionData.getAssetId()));

			Serialization.serializeBigDecimal(bytes, messageTransactionData.getAmount());

			bytes.write(Ints.toByteArray(messageTransactionData.getData().length));

			bytes.write(messageTransactionData.getData());

			bytes.write((byte) (messageTransactionData.getIsEncrypted() ? 1 : 0));

			bytes.write((byte) (messageTransactionData.getIsText() ? 1 : 0));

			Serialization.serializeBigDecimal(bytes, messageTransactionData.getFee());

			if (messageTransactionData.getSignature() != null)
				bytes.write(messageTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
