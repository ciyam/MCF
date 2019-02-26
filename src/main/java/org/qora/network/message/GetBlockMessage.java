package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.transform.block.BlockTransformer;

public class GetBlockMessage extends Message {

	private static final int BLOCK_SIGNATURE_LENGTH = BlockTransformer.BLOCK_SIGNATURE_LENGTH;

	private byte[] signature;

	public GetBlockMessage(byte[] signature) {
		this(-1, signature);
	}

	private GetBlockMessage(int id, byte[] signature) {
		super(id, MessageType.GET_BLOCK);

		this.signature = signature;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		if (bytes.remaining() != BLOCK_SIGNATURE_LENGTH)
			return null;

		byte[] signature = new byte[BLOCK_SIGNATURE_LENGTH];

		bytes.get(signature);

		return new GetBlockMessage(id, signature);
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
