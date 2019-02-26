package org.qora.network.message;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class PingMessage extends Message {

	public PingMessage() {
		this(-1);
	}

	private PingMessage(int id) {
		super(id, MessageType.PING);
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		return new PingMessage(id);
	}

	@Override
	protected byte[] toData() {
		return new byte[0];
	}

}
