package org.qora.transform;

public abstract class Transformer {

	public static final int BOOLEAN_LENGTH = 1;
	public static final int BYTE_LENGTH = 1;
	public static final int INT_LENGTH = 4;
	public static final int LONG_LENGTH = 8;
	public static final int BIG_DECIMAL_LENGTH = 8;

	// Raw, not Base58-encoded
	public static final int ADDRESS_LENGTH = 25;

	public static final int PUBLIC_KEY_LENGTH = 32;
	public static final int SIGNATURE_LENGTH = 64;
	public static final int TIMESTAMP_LENGTH = LONG_LENGTH;

	public static final int MD5_LENGTH = 16;
	public static final int SHA256_LENGTH = 32;

}
