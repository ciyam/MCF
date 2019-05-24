package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.network.Network;

public class PeerVerifyMessage extends Message {

	private byte[] verificationCode;

	public PeerVerifyMessage(byte[] verificationCode) {
		this(-1, verificationCode);
	}

	private PeerVerifyMessage(int id, byte[] verificationCode) {
		super(id, MessageType.PEER_VERIFY);

		this.verificationCode = verificationCode;
	}

	public byte[] getVerificationCode() {
		return this.verificationCode;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		if (bytes.remaining() != Network.PEER_ID_LENGTH)
			return null;

		byte[] verificationCode = new byte[Network.PEER_ID_LENGTH];
		bytes.get(verificationCode);

		return new PeerVerifyMessage(id, verificationCode);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.verificationCode);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
