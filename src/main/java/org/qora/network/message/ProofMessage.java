package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import com.google.common.primitives.Longs;

public class ProofMessage extends Message {

	private long timestamp;
	private long salt;
	private long nonce;

	public ProofMessage(long timestamp, long salt, long nonce) {
		this(-1, timestamp, salt, nonce);
	}

	private ProofMessage(int id, long timestamp, long salt, long nonce) {
		super(id, MessageType.PROOF);

		this.timestamp = timestamp;
		this.salt = salt;
		this.nonce = nonce;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public long getSalt() {
		return this.salt;
	}

	public long getNonce() {
		return this.nonce;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		long timestamp = bytes.getLong();
		long salt = bytes.getLong();
		long nonce = bytes.getLong();

		return new ProofMessage(id, timestamp, salt, nonce);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Longs.toByteArray(this.timestamp));
			bytes.write(Longs.toByteArray(this.salt));
			bytes.write(Longs.toByteArray(this.nonce));

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
