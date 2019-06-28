package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import com.google.common.primitives.Ints;

public class HeightMessage extends Message {

	private int height;

	public HeightMessage(int height) {
		this(-1, height);
	}

	private HeightMessage(int id, int height) {
		super(id, MessageType.HEIGHT);

		this.height = height;
	}

	public int getHeight() {
		return this.height;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		int height = bytes.getInt();

		return new HeightMessage(id, height);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(this.height));

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
