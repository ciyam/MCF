package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.transform.Transformer;
import org.qora.transform.block.BlockTransformer;

import com.google.common.primitives.Ints;

public class GetSignaturesV2Message extends Message {

	private static final int BLOCK_SIGNATURE_LENGTH = BlockTransformer.BLOCK_SIGNATURE_LENGTH;
	private static final int NUMBER_REQUESTED_LENGTH = Transformer.INT_LENGTH;

	private byte[] parentSignature;
	private int numberRequested;

	public GetSignaturesV2Message(byte[] parentSignature, int numberRequested) {
		this(-1, parentSignature, numberRequested);
	}

	private GetSignaturesV2Message(int id, byte[] parentSignature, int numberRequested) {
		super(id, MessageType.GET_SIGNATURES_V2);

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
		if (bytes.remaining() != BLOCK_SIGNATURE_LENGTH + NUMBER_REQUESTED_LENGTH)
			return null;

		byte[] parentSignature = new byte[BLOCK_SIGNATURE_LENGTH];
		bytes.get(parentSignature);

		int numberRequested = bytes.getInt();

		return new GetSignaturesV2Message(id, parentSignature, numberRequested);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.parentSignature);

			bytes.write(Ints.toByteArray(this.numberRequested));

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
