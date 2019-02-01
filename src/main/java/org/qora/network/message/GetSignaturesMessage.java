package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.transform.block.BlockTransformer;

public class GetSignaturesMessage extends Message {

	private static final int BLOCK_SIGNATURE_LENGTH = BlockTransformer.BLOCK_SIGNATURE_LENGTH;

	private byte[] parentSignature;

	public GetSignaturesMessage(byte[] parentSignature) {
		this(-1, parentSignature);
	}

	private GetSignaturesMessage(int id, byte[] parentSignature) {
		super(id, MessageType.GET_SIGNATURES);

		this.parentSignature = parentSignature;
	}

	public byte[] getParentSignature() {
		return this.parentSignature;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		if (bytes.remaining() != BLOCK_SIGNATURE_LENGTH)
			return null;

		byte[] parentSignature = new byte[BLOCK_SIGNATURE_LENGTH];

		bytes.get(parentSignature);

		return new GetSignaturesMessage(id, parentSignature);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.parentSignature);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
