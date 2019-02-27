package org.qora.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.qora.block.BlockChain;
import org.qora.data.transaction.AccountFlagsTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.utils.Serialization;

import com.google.common.primitives.Ints;

public class AccountFlagsTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int TARGET_LENGTH = ADDRESS_LENGTH;
	private static final int AND_MASK_LENGTH = INT_LENGTH;
	private static final int OR_MASK_LENGTH = INT_LENGTH;
	private static final int XOR_MASK_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = TARGET_LENGTH + AND_MASK_LENGTH + OR_MASK_LENGTH + XOR_MASK_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.GROUP_INVITE.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("account's public key", TransformationType.PUBLIC_KEY);
		layout.add("target account's address", TransformationType.ADDRESS);
		layout.add("flags AND mask", TransformationType.INT);
		layout.add("flags OR mask", TransformationType.INT);
		layout.add("flags XOR mask", TransformationType.INT);
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

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String target = Serialization.deserializeAddress(byteBuffer);

		int andMask = byteBuffer.getInt();
		int orMask = byteBuffer.getInt();
		int xorMask = byteBuffer.getInt();

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new AccountFlagsTransactionData(timestamp, txGroupId, reference, creatorPublicKey, target, andMask, orMask, xorMask, fee, signature);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return getBaseLength(transactionData) + EXTRAS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			AccountFlagsTransactionData accountFlagsTransactionData = (AccountFlagsTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, accountFlagsTransactionData.getTarget());

			bytes.write(Ints.toByteArray(accountFlagsTransactionData.getAndMask()));
			bytes.write(Ints.toByteArray(accountFlagsTransactionData.getOrMask()));
			bytes.write(Ints.toByteArray(accountFlagsTransactionData.getXorMask()));

			Serialization.serializeBigDecimal(bytes, accountFlagsTransactionData.getFee());

			if (accountFlagsTransactionData.getSignature() != null)
				bytes.write(accountFlagsTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
