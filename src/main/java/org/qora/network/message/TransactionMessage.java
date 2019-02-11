package org.qora.network.message;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.data.transaction.TransactionData;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.TransactionTransformer;

public class TransactionMessage extends Message {

	private TransactionData transactionData;

	public TransactionMessage(TransactionData transactionData) {
		this(-1, transactionData);
	}

	private TransactionMessage(int id, TransactionData transactionData) {
		super(id, MessageType.TRANSACTION);

		this.transactionData = transactionData;
	}

	public TransactionData getTransactionData() {
		return this.transactionData;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws UnsupportedEncodingException {
		try {
			TransactionData transactionData = TransactionTransformer.fromByteBuffer(byteBuffer);

			return new TransactionMessage(id, transactionData);
		} catch (TransformationException e) {
			return null;
		}
	}

	@Override
	protected byte[] toData() {
		if (this.transactionData == null)
			return null;

		try {
			return TransactionTransformer.toBytes(this.transactionData);
		} catch (TransformationException e) {
			return null;
		}
	}

}
