package org.qora.network.message;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class GetUnconfirmedTransactionsMessage extends Message {

	public GetUnconfirmedTransactionsMessage() {
		this(-1);
	}

	private GetUnconfirmedTransactionsMessage(int id) {
		super(id, MessageType.GET_UNCONFIRMED_TRANSACTIONS);
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		return new GetUnconfirmedTransactionsMessage(id);
	}

	@Override
	protected byte[] toData() {
		return new byte[0];
	}

}
