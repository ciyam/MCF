package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.json.simple.JSONObject;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.data.transaction.GenesisTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class GenesisTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int AMOUNT_LENGTH = LONG_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;

	// Note that Genesis transactions don't require reference, fee or signature:
	private static final int TYPELESS_LENGTH_V1 = TIMESTAMP_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;
	private static final int TYPELESS_LENGTH_V4 = TIMESTAMP_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH + ASSET_ID_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.GENESIS.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("recipient", TransformationType.ADDRESS);
		layout.add("amount", TransformationType.AMOUNT);
		layout.add("asset ID", TransformationType.LONG);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		String recipient = Serialization.deserializeAddress(byteBuffer);

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		long assetId = Asset.QORA;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			assetId = byteBuffer.getLong();

		return new GenesisTransactionData(recipient, amount, assetId, timestamp);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		if (transactionData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp())
			return TYPE_LENGTH + TYPELESS_LENGTH_V1;
		else
			return TYPE_LENGTH + TYPELESS_LENGTH_V4;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GenesisTransactionData genesisTransactionData = (GenesisTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(genesisTransactionData.getType().value));
			bytes.write(Longs.toByteArray(genesisTransactionData.getTimestamp()));

			Serialization.serializeAddress(bytes, genesisTransactionData.getRecipient());
			Serialization.serializeBigDecimal(bytes, genesisTransactionData.getAmount());

			if (genesisTransactionData.getTimestamp() >= BlockChain.getInstance().getQoraV2Timestamp())
				bytes.write(Longs.toByteArray(genesisTransactionData.getAssetId()));

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
			json.put("assetId", genesisTransactionData.getAssetId());
		} catch (ClassCastException e) {
			throw new TransformationException(e);
		}

		return json;
	}

}
