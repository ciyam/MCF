package org.qora.utils;

import java.util.Comparator;

import com.google.common.hash.HashCode;

public class ByteArray implements Comparable<ByteArray> {

	private static final Comparator<ByteArray> COMPARATOR;
	static {
		COMPARATOR = Comparator.comparing(byteArray -> byteArray.comparable);
	}

	private final String comparable;

	public final byte[] raw;

	public ByteArray(byte[] content) {
		this.comparable = HashCode.fromBytes(content).toString();
		this.raw = content;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;

		if (!(other instanceof ByteArray))
			return false;

		ByteArray otherByteArray = (ByteArray) other;

		return this.comparable.equals(otherByteArray.comparable);
	}

	@Override
	public int hashCode() {
		return this.comparable.hashCode();
	}

	@Override
	public int compareTo(ByteArray other) {
		return COMPARATOR.compare(this, other);
	}

}
