package org.qora.transform.transaction;

import java.nio.ByteBuffer;

import org.qora.data.transaction.TransactionData;
import org.qora.transform.TransformationException;

public class AtTransactionTransformer extends TransactionTransformer {

	protected static final TransactionLayout layout = null;

	// Property lengths
	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		throw new TransformationException("Serialized AT Transactions should not exist!");
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		// AT Transactions aren't serialized so don't take up any space in the block.
		return 0;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		throw new TransformationException("Serialized AT Transactions should not exist!");
	}

}
