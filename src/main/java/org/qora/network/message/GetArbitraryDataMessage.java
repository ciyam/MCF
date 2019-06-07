package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.transform.Transformer;

public class GetArbitraryDataMessage extends Message {

	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	private byte[] signature;

	public GetArbitraryDataMessage(byte[] signature) {
		this(-1, signature);
	}

	private GetArbitraryDataMessage(int id, byte[] signature) {
		super(id, MessageType.GET_ARBITRARY_DATA);

		this.signature = signature;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		if (bytes.remaining() != SIGNATURE_LENGTH)
			return null;

		byte[] signature = new byte[SIGNATURE_LENGTH];

		bytes.get(signature);

		return new GetArbitraryDataMessage(id, signature);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.signature);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
