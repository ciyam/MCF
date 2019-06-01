package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.block.BlockChain;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateNameTransactionData;
import org.qora.naming.Name;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;

public class UpdateNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = OWNER_LENGTH + NAME_SIZE_LENGTH + DATA_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.UPDATE_NAME.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("name owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("name's new owner", TransformationType.ADDRESS);
		layout.add("name length", TransformationType.INT);
		layout.add("name", TransformationType.STRING);
		layout.add("new data length", TransformationType.INT);
		layout.add("new data", TransformationType.STRING);
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

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String newOwner = Serialization.deserializeAddress(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);

		String newData = Serialization.deserializeSizedString(byteBuffer, Name.MAX_DATA_SIZE);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, ownerPublicKey, fee, signature);

		return new UpdateNameTransactionData(baseTransactionData, newOwner, name, newData);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(updateNameTransactionData.getName())
				+ Utf8.encodedLength(updateNameTransactionData.getNewData());
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, updateNameTransactionData.getNewOwner());

			Serialization.serializeSizedString(bytes, updateNameTransactionData.getName());

			Serialization.serializeSizedString(bytes, updateNameTransactionData.getNewData());

			Serialization.serializeBigDecimal(bytes, updateNameTransactionData.getFee());

			if (updateNameTransactionData.getSignature() != null)
				bytes.write(updateNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
