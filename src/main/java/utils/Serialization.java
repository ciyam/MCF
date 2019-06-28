package utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import com.google.common.primitives.Ints;

import transform.TransformationException;
import transform.Transformer;

public class Serialization {

	/**
	 * Convert BigDecimal, unscaled, to byte[] then prepend with zero bytes to specified length.
	 * 
	 * @param amount
	 * @param length
	 * @return byte[]
	 * @throws IOException
	 */
	public static byte[] serializeBigDecimal(BigDecimal amount, int length) throws IOException {
		// Note: we call .setScale(8) here to normalize values, especially values from API as they can have varying scale
		// (At least until the BigDecimal XmlAdapter works - see data/package-info.java)
		byte[] amountBytes = amount.setScale(8).unscaledValue().toByteArray();
		byte[] output = new byte[length];
		System.arraycopy(amountBytes, 0, output, length - amountBytes.length, amountBytes.length);
		return output;
	}

	/**
	 * Convert BigDecimal, unscaled, to byte[] then prepend with zero bytes to fixed length of 8.
	 * 
	 * @param amount
	 * @return byte[]
	 * @throws IOException
	 */
	public static byte[] serializeBigDecimal(BigDecimal amount) throws IOException {
		return serializeBigDecimal(amount, 8);
	}

	/**
	 * Write to ByteBuffer a BigDecimal, unscaled, prepended with zero bytes to specified length.
	 * 
	 * @param ByteArrayOutputStream
	 * @param amount
	 * @param length
	 * @throws IOException
	 */
	public static void serializeBigDecimal(ByteArrayOutputStream bytes, BigDecimal amount, int length) throws IOException {
		bytes.write(serializeBigDecimal(amount, length));
	}

	/**
	 * Write to ByteBuffer a BigDecimal, unscaled, prepended with zero bytes to fixed length of 8.
	 * 
	 * @param ByteArrayOutputStream
	 * @param amount
	 * @throws IOException
	 */
	public static void serializeBigDecimal(ByteArrayOutputStream bytes, BigDecimal amount) throws IOException {
		serializeBigDecimal(bytes, amount, 8);
	}

	public static BigDecimal deserializeBigDecimal(ByteBuffer byteBuffer, int length) {
		byte[] bytes = new byte[length];
		byteBuffer.get(bytes);
		return new BigDecimal(new BigInteger(bytes), 8);
	}

	public static BigDecimal deserializeBigDecimal(ByteBuffer byteBuffer) {
		return Serialization.deserializeBigDecimal(byteBuffer, 8);
	}

	public static void serializeAddress(ByteArrayOutputStream bytes, String address) throws IOException {
		bytes.write(Base58.decode(address));
	}

	public static String deserializeAddress(ByteBuffer byteBuffer) {
		byte[] bytes = new byte[Transformer.ADDRESS_LENGTH];
		byteBuffer.get(bytes);
		return Base58.encode(bytes);
	}

	public static byte[] deserializePublicKey(ByteBuffer byteBuffer) {
		byte[] bytes = new byte[Transformer.PUBLIC_KEY_LENGTH];
		byteBuffer.get(bytes);
		return bytes;
	}

	public static void serializeSizedString(ByteArrayOutputStream bytes, String string) throws UnsupportedEncodingException, IOException {
		byte[] stringBytes = string.getBytes("UTF-8");
		bytes.write(Ints.toByteArray(stringBytes.length));
		bytes.write(string.getBytes("UTF-8"));
	}

	public static String deserializeSizedString(ByteBuffer byteBuffer, int maxSize) throws TransformationException {
		int size = byteBuffer.getInt();
		if (size > maxSize)
			throw new TransformationException("Serialized string too long");

		if (size > byteBuffer.remaining())
			throw new TransformationException("Byte data too short for serialized string");

		byte[] bytes = new byte[size];
		byteBuffer.get(bytes);

		try {
			return new String(bytes, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new TransformationException("UTF-8 charset unsupported during string deserialization");
		}
	}

}
