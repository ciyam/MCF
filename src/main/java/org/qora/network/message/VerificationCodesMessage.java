package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.network.Network;

public class VerificationCodesMessage extends Message {

	private static final int TOTAL_LENGTH = Network.PEER_ID_LENGTH + Network.PEER_ID_LENGTH;

	private byte[] verificationCodeSent;
	private byte[] verificationCodeExpected;

	public VerificationCodesMessage(byte[] verificationCodeSent, byte[] verificationCodeExpected) {
		this(-1, verificationCodeSent, verificationCodeExpected);
	}

	private VerificationCodesMessage(int id, byte[] verificationCodeSent, byte[] verificationCodeExpected) {
		super(id, MessageType.VERIFICATION_CODES);

		this.verificationCodeSent = verificationCodeSent;
		this.verificationCodeExpected = verificationCodeExpected;
	}

	public byte[] getVerificationCodeSent() {
		return this.verificationCodeSent;
	}

	public byte[] getVerificationCodeExpected() {
		return this.verificationCodeExpected;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		if (bytes.remaining() != TOTAL_LENGTH)
			return null;

		byte[] verificationCodeSent = new byte[Network.PEER_ID_LENGTH];
		bytes.get(verificationCodeSent);

		byte[] verificationCodeExpected = new byte[Network.PEER_ID_LENGTH];
		bytes.get(verificationCodeExpected);

		return new VerificationCodesMessage(id, verificationCodeSent, verificationCodeExpected);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.verificationCodeSent);

			bytes.write(this.verificationCodeExpected);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
