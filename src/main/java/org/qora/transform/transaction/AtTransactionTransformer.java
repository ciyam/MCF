package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.data.transaction.ATTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class AtTransactionTransformer extends TransactionTransformer {

	private static final int SENDER_LENGTH = ADDRESS_LENGTH;
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int AMOUNT_LENGTH = BIG_DECIMAL_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = SENDER_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH + ASSET_ID_LENGTH + DATA_SIZE_LENGTH;

	protected static final TransactionLayout layout = null;

	// Property lengths
	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		throw new TransformationException("Serialized AT Transactions should not exist!");
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		ATTransactionData atTransactionData = (ATTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + atTransactionData.getMessage().length;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			ATTransactionData atTransactionData = (ATTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(atTransactionData.getType().value));
			bytes.write(Longs.toByteArray(atTransactionData.getTimestamp()));
			bytes.write(atTransactionData.getReference());

			Serialization.serializeAddress(bytes, atTransactionData.getATAddress());

			Serialization.serializeAddress(bytes, atTransactionData.getRecipient());

			// Only emit amount if greater than zero (safer than checking assetId)
			if (atTransactionData.getAmount().compareTo(BigDecimal.ZERO) > 0) {
				Serialization.serializeBigDecimal(bytes, atTransactionData.getAmount());
				bytes.write(Longs.toByteArray(atTransactionData.getAssetId()));
			}

			byte[] message = atTransactionData.getMessage();
			if (message.length > 0) {
				bytes.write(Ints.toByteArray(message.length));
				bytes.write(message);
			} else {
				bytes.write(Ints.toByteArray(0));
			}

			Serialization.serializeBigDecimal(bytes, atTransactionData.getFee());

			if (atTransactionData.getSignature() != null)
				bytes.write(atTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
