package org.qora.network.message;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class GetPeersMessage extends Message {

	public GetPeersMessage() {
		this(-1);
	}

	private GetPeersMessage(int id) {
		super(id, MessageType.GET_PEERS);
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		return new GetPeersMessage(id);
	}

	@Override
	protected byte[] toData() {
		return new byte[0];
	}

}
