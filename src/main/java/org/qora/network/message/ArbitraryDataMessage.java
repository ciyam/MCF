package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.transform.Transformer;

import com.google.common.primitives.Ints;

public class ArbitraryDataMessage extends Message {

	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	private byte[] signature;
	private byte[] data;

	public ArbitraryDataMessage(byte[] signature, byte[] data) {
		this(-1, signature, data);
	}

	private ArbitraryDataMessage(int id, byte[] signature, byte[] data) {
		super(id, MessageType.ARBITRARY_DATA);

		this.signature = signature;
		this.data = data;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public byte[] getData() {
		return this.data;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws UnsupportedEncodingException {
		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		int dataLength = byteBuffer.getInt();

		if (byteBuffer.remaining() != dataLength)
			return null;

		byte[] data = new byte[dataLength];
		byteBuffer.get(data);

		return new ArbitraryDataMessage(id, signature, data);
	}

	@Override
	protected byte[] toData() {
		if (this.data == null)
			return null;

		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.signature);

			bytes.write(Ints.toByteArray(this.data.length));

			bytes.write(this.data);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
