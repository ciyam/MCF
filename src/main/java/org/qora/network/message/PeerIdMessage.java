package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.network.Network;

public class PeerIdMessage extends Message {

	private byte[] peerId;

	public PeerIdMessage(byte[] peerId) {
		this(-1, peerId);
	}

	private PeerIdMessage(int id, byte[] peerId) {
		super(id, MessageType.PEER_ID);

		this.peerId = peerId;
	}

	public byte[] getPeerId() {
		return this.peerId;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		if (bytes.remaining() != Network.PEER_ID_LENGTH)
			return null;

		byte[] peerId = new byte[Network.PEER_ID_LENGTH];

		bytes.get(peerId);

		return new PeerIdMessage(id, peerId);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.peerId);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
