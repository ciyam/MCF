package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.data.transaction.UpdateAssetTransactionData;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.primitives.Longs;

public class UpdateAssetTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;
	private static final int NEW_OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NEW_DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int NEW_DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = ASSET_ID_LENGTH + NEW_OWNER_LENGTH + NEW_DESCRIPTION_SIZE_LENGTH
			+ NEW_DATA_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.ISSUE_ASSET.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("asset owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("asset ID", TransformationType.LONG);
		layout.add("asset new owner", TransformationType.ADDRESS);
		layout.add("asset new description length", TransformationType.INT);
		layout.add("asset new description", TransformationType.STRING);
		layout.add("asset new data length", TransformationType.INT);
		layout.add("asset new data", TransformationType.STRING);
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

		long assetId = byteBuffer.getLong();

		String newOwner = Serialization.deserializeAddress(byteBuffer);

		String newDescription = Serialization.deserializeSizedString(byteBuffer, Asset.MAX_DESCRIPTION_SIZE);

		String newData = Serialization.deserializeSizedString(byteBuffer, Asset.MAX_DATA_SIZE);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, ownerPublicKey, fee, signature);

		return new UpdateAssetTransactionData(baseTransactionData, assetId, newOwner, newDescription, newData);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		UpdateAssetTransactionData updateAssetTransactionData = (UpdateAssetTransactionData) transactionData;

		int dataLength = getBaseLength(transactionData) + EXTRAS_LENGTH
				+ Utf8.encodedLength(updateAssetTransactionData.getNewDescription())
				+ Utf8.encodedLength(updateAssetTransactionData.getNewData());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			UpdateAssetTransactionData updateAssetTransactionData = (UpdateAssetTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			bytes.write(Longs.toByteArray(updateAssetTransactionData.getAssetId()));

			Serialization.serializeAddress(bytes, updateAssetTransactionData.getNewOwner());

			Serialization.serializeSizedString(bytes, updateAssetTransactionData.getNewDescription());

			Serialization.serializeSizedString(bytes, updateAssetTransactionData.getNewData());

			Serialization.serializeBigDecimal(bytes, updateAssetTransactionData.getFee());

			if (updateAssetTransactionData.getSignature() != null)
				bytes.write(updateAssetTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
