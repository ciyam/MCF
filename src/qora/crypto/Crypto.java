package qora.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import qora.account.Account;
import qora.account.PrivateKeyAccount;
import utils.Base58;
import utils.Pair;

public class Crypto {

	private static final Logger LOGGER = LogManager.getLogger(Crypto.class);

	public static final byte ADDRESS_VERSION = 58;
	public static final byte AT_ADDRESS_VERSION = 23;

	public static byte[] digest(byte[] input) {
		try {
			// SHA2-256
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return sha256.digest(input);
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	public static byte[] doubleDigest(byte[] input) {
		// Two rounds of SHA2-256
		return digest(digest(input));
	}

	public static Pair<byte[], byte[]> createKeyPair(byte[] seed) {
		try {
			// Generate private and public key pair
			return Ed25519.createKeyPair(seed);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	@SuppressWarnings("deprecation")
	private static String toAddress(byte addressVersion, byte[] input) {
		// SHA2-256 input to create new data and of known size
		byte[] inputHash = digest(input);

		// Use BROKEN RIPEMD160 to create shorter address
		BrokenMD160 brokenMD160 = new BrokenMD160();
		inputHash = brokenMD160.digest(inputHash);

		// Create address data using above hash and addressVersion (prepended)
		byte[] addressBytes = new byte[inputHash.length + 1];
		System.arraycopy(inputHash, 0, addressBytes, 1, inputHash.length);
		addressBytes[0] = addressVersion;

		// Generate checksum
		byte[] checksum = doubleDigest(addressBytes);

		// Append checksum
		byte[] addressWithChecksum = new byte[addressBytes.length + 4];
		System.arraycopy(addressBytes, 0, addressWithChecksum, 0, addressBytes.length);
		System.arraycopy(checksum, 0, addressWithChecksum, addressBytes.length, 4);

		// Return Base58-encoded
		return Base58.encode(addressWithChecksum);
	}

	public static String toAddress(byte[] publicKey) {
		return toAddress(ADDRESS_VERSION, publicKey);
	}

	public static String toATAddress(byte[] signature) {
		return toAddress(AT_ADDRESS_VERSION, signature);
	}

	public static boolean isValidAddress(String address) {
		byte[] addressBytes;
		
		try {
			// Attempt Base58 decoding
			addressBytes = Base58.decode(address);
		} catch (NumberFormatException e) {
			return false;
		}

		// Check address length
		if (addressBytes.length != Account.ADDRESS_LENGTH)
			return false;

		// Check by address type
		switch (addressBytes[0]) {
			case ADDRESS_VERSION:
			case AT_ADDRESS_VERSION:
				byte[] addressWithoutChecksum = Arrays.copyOf(addressBytes, addressBytes.length - 4);
				byte[] passedChecksum = Arrays.copyOfRange(addressWithoutChecksum, addressBytes.length - 4, addressBytes.length);
				
				byte[] generatedChecksum = doubleDigest(addressWithoutChecksum);
				return Arrays.equals(passedChecksum, generatedChecksum);
				
			default:
				return false;
		}
	}

	public static byte[] sign(PrivateKeyAccount account, byte[] message) {
		try {
			// GET SIGNATURE
			return Ed25519.sign(account.getKeyPair(), message);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(),e);
			return new byte[64];
		}
	}

	public static boolean verify(byte[] publicKey, byte[] signature, byte[] message) {
		try {
			// VERIFY SIGNATURE
			return Ed25519.verify(signature, message, publicKey);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(),e);
			return false;
		}
	}

}
