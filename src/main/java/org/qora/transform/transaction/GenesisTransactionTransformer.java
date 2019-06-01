package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.account.GenesisAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.data.transaction.BaseTransactionData;
import org.qora.data.transaction.GenesisTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class GenesisTransactionTransformer extends TransactionTransformer {

	// Note that Genesis transactions don't require reference, fee or signature

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int AMOUNT_LENGTH = LONG_LENGTH;
	private static final int ASSET_ID_LENGTH = LONG_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.GENESIS.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("recipient", TransformationType.ADDRESS);
		layout.add("amount", TransformationType.AMOUNT);
		layout.add("asset ID", TransformationType.LONG);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		String recipient = Serialization.deserializeAddress(byteBuffer);

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		long assetId = Asset.QORA;
		if (timestamp >= BlockChain.getInstance().getQoraV2Timestamp())
			assetId = byteBuffer.getLong();

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, null, GenesisAccount.PUBLIC_KEY, BigDecimal.ZERO, null);

		return new GenesisTransactionData(baseTransactionData, recipient, amount, assetId);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		if (transactionData.getTimestamp() < BlockChain.getInstance().getQoraV2Timestamp())
			return TYPE_LENGTH + TIMESTAMP_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;

		// Qora V2+
		return TYPE_LENGTH + TIMESTAMP_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH + ASSET_ID_LENGTH;
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

}
