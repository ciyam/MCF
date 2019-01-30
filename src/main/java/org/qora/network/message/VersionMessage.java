package org.qora.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qora.utils.Serialization;

import com.google.common.primitives.Longs;

public class VersionMessage extends Message {

	private long buildTimestamp;
	private String versionString;

	public VersionMessage(long buildTimestamp, String versionString) {
		this(-1, buildTimestamp, versionString);
	}

	private VersionMessage(int id, long buildTimestamp, String versionString) {
		super(id, MessageType.VERSION);

		this.buildTimestamp = buildTimestamp;
		this.versionString = versionString;
	}

	public long getBuildTimestamp() {
		return this.buildTimestamp;
	}

	public String getVersionString() {
		return this.versionString;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		long buildTimestamp = bytes.getLong();

		int versionStringLength = bytes.getInt();

		if (versionStringLength != bytes.remaining())
			return null;

		byte[] versionBytes = new byte[versionStringLength];
		bytes.get(versionBytes);

		String versionString = new String(versionBytes, "UTF-8");

		return new VersionMessage(id, buildTimestamp, versionString);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Longs.toByteArray(this.buildTimestamp));

			Serialization.serializeSizedString(bytes, this.versionString);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
