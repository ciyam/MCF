package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.transform.Transformer;
import org.qora.transform.block.BlockTransformer;

public class GetBlockSummariesMessage extends Message {

	private static final int BLOCK_SIGNATURE_LENGTH = BlockTransformer.BLOCK_SIGNATURE_LENGTH;

	private byte[] parentSignature;
	private int numberRequested;

	public GetBlockSummariesMessage(byte[] parentSignature, int numberRequested) {
		this(-1, parentSignature, numberRequested);
	}

	private GetBlockSummariesMessage(int id, byte[] parentSignature, int numberRequested) {
		super(id, MessageType.GET_BLOCK_SUMMARIES);

		this.parentSignature = parentSignature;
		this.numberRequested = numberRequested;
	}

	public byte[] getParentSignature() {
		return this.parentSignature;
	}

	public int getNumberRequested() {
		return this.numberRequested;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		if (bytes.remaining() != BLOCK_SIGNATURE_LENGTH + Transformer.INT_LENGTH)
			return null;

		byte[] parentSignature = new byte[BLOCK_SIGNATURE_LENGTH];
		bytes.get(parentSignature);

		int numberRequested = bytes.getInt();

		return new GetBlockSummariesMessage(id, parentSignature, numberRequested);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.parentSignature);

			bytes.write(this.numberRequested);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
