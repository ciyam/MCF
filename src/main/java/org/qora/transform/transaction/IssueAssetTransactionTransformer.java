package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.base.Utf8;
import com.google.common.primitives.Longs;

public class IssueAssetTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int QUANTITY_LENGTH = LONG_LENGTH;
	private static final int IS_DIVISIBLE_LENGTH = BOOLEAN_LENGTH;
	private static final int ASSET_REFERENCE_LENGTH = REFERENCE_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH + QUANTITY_LENGTH
			+ IS_DIVISIBLE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.ISSUE_ASSET.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("asset issuer's public key", TransformationType.PUBLIC_KEY);
		layout.add("asset owner", TransformationType.ADDRESS);
		layout.add("asset name length", TransformationType.INT);
		layout.add("asset name", TransformationType.STRING);
		layout.add("asset description length", TransformationType.INT);
		layout.add("asset description", TransformationType.STRING);
		layout.add("asset quantity", TransformationType.LONG);
		layout.add("can asset quantities be fractional?", TransformationType.BOOLEAN);
		layout.add("asset data length", TransformationType.INT);
		layout.add("asset data", TransformationType.STRING);
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

		byte[] issuerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String owner = Serialization.deserializeAddress(byteBuffer);

		String assetName = Serialization.deserializeSizedString(byteBuffer, Asset.MAX_NAME_SIZE);

		String description = Serialization.deserializeSizedString(byteBuffer, Asset.MAX_DESCRIPTION_SIZE);

		long quantity = byteBuffer.getLong();

		boolean isDivisible = byteBuffer.get() != 0;

		// in v2, assets have "data" field
		String data = null;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			data = Serialization.deserializeSizedString(byteBuffer, Asset.MAX_DATA_SIZE);

		byte[] assetReference = new byte[ASSET_REFERENCE_LENGTH];
		// In v1, IssueAssetTransaction uses Asset.parse which also deserializes
		// reference.
		if (timestamp < BlockChain.getInstance().getQoraV2Timestamp())
			byteBuffer.get(assetReference);

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new IssueAssetTransactionData(timestamp, txGroupId, reference, issuerPublicKey, owner, assetName,
				description, quantity, isDivisible, data, fee, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) transactionData;

		int dataLength = getBaseLength(transactionData) + EXTRAS_LENGTH
				+ Utf8.encodedLength(issueAssetTransactionData.getAssetName())
				+ Utf8.encodedLength(issueAssetTransactionData.getDescription());

		// In v2, assets have "data" field
		if (transactionData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp())
			dataLength += DATA_SIZE_LENGTH + Utf8.encodedLength(issueAssetTransactionData.getData());

		// In v1, IssueAssetTransaction uses Asset.toBytes which also serializes
		// reference.
		if (transactionData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp())
			dataLength += ASSET_REFERENCE_LENGTH;

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, issueAssetTransactionData.getOwner());

			Serialization.serializeSizedString(bytes, issueAssetTransactionData.getAssetName());

			Serialization.serializeSizedString(bytes, issueAssetTransactionData.getDescription());

			bytes.write(Longs.toByteArray(issueAssetTransactionData.getQuantity()));
			bytes.write((byte) (issueAssetTransactionData.getIsDivisible() ? 1 : 0));

			// In v2, assets have "data"
			if (transactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
				Serialization.serializeSizedString(bytes, issueAssetTransactionData.getData());

			// In v1, IssueAssetTransaction uses Asset.toBytes which also
			// serializes Asset's reference which is the IssueAssetTransaction's
			// signature
			if (transactionData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp()) {
				byte[] assetReference = issueAssetTransactionData.getSignature();
				if (assetReference != null)
					bytes.write(assetReference);
				else
					bytes.write(new byte[ASSET_REFERENCE_LENGTH]);
			}

			Serialization.serializeBigDecimal(bytes, issueAssetTransactionData.getFee());

			if (issueAssetTransactionData.getSignature() != null)
				bytes.write(issueAssetTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	/**
	 * In Qora v1, the bytes used for verification have asset's reference zeroed
	 * so we need to test for v1-ness and adjust the bytes accordingly.
	 * 
	 * @param transactionData
	 * @return byte[]
	 * @throws TransformationException
	 */
	public static byte[] toBytesForSigningImpl(TransactionData transactionData) throws TransformationException {
		byte[] bytes = TransactionTransformer.toBytesForSigningImpl(transactionData);

		if (transactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
			return bytes;

		// Special v1 version

		// Zero duplicate signature/reference
		int start = bytes.length - ASSET_REFERENCE_LENGTH - FEE_LENGTH; // before
																		// asset
																		// reference
																		// (and
																		// fee)
		int end = start + ASSET_REFERENCE_LENGTH;
		Arrays.fill(bytes, start, end, (byte) 0);

		return bytes;
	}

}
