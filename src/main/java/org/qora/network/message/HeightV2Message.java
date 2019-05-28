package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.transform.Transformer;
import org.qora.transform.block.BlockTransformer;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class HeightV2Message extends Message {

	private int height;
	private byte[] signature;
	private long timestamp;
	private byte[] generator;

	public HeightV2Message(int height, byte[] signature, long timestamp, byte[] generator) {
		this(-1, height, signature, timestamp, generator);
	}

	private HeightV2Message(int id, int height, byte[] signature, long timestamp, byte[] generator) {
		super(id, MessageType.HEIGHT_V2);

		this.height = height;
		this.signature = signature;
		this.timestamp = timestamp;
		this.generator = generator;
	}

	public int getHeight() {
		return this.height;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getGenerator() {
		return this.generator;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		int height = bytes.getInt();

		byte[] signature = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
		bytes.get(signature);

		long timestamp = bytes.getLong();

		byte[] generator = new byte[Transformer.PUBLIC_KEY_LENGTH];
		bytes.get(generator);

		return new HeightV2Message(id, height, signature, timestamp, generator);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(this.height));

			bytes.write(this.signature);

			bytes.write(Longs.toByteArray(this.timestamp));

			bytes.write(this.generator);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
