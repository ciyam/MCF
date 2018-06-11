package transform;

public abstract class Transformer {

	public static final int INT_LENGTH = 4;
	public static final int LONG_LENGTH = 8;

	// Raw, not Base58-encoded
	public static final int ADDRESS_LENGTH = 25;

	public static final int PUBLIC_KEY_LENGTH = 32;
	public static final int SIGNATURE_LENGTH = 64; 
	public static final int TIMESTAMP_LENGTH = LONG_LENGTH;

}
