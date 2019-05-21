package org.qora.crypto;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.math.ec.rfc7748.X25519;
import org.bouncycastle.math.ec.rfc7748.X25519Field;
import org.bouncycastle.math.ec.rfc8032.Ed25519;

/** Additions to BouncyCastle providing Ed25519 to X25519 key conversion. */
public class BouncyCastle25519 {

	private static final Class<?> pointExtClass;
	private static final Constructor<?> pointExtCtor;
	private static final Method decodePointVarMethod;
	private static final Field yField;

	static {
		try {
			Class<?> ed25519Class = Ed25519.class;
			pointExtClass = Arrays.stream(ed25519Class.getDeclaredClasses()).filter(clazz -> clazz.getSimpleName().equals("PointExt")).findFirst().get();
			if (pointExtClass == null)
				throw new ClassNotFoundException("Can't locate PointExt inner class inside Ed25519");

			decodePointVarMethod = ed25519Class.getDeclaredMethod("decodePointVar", byte[].class, int.class, boolean.class, pointExtClass);
			decodePointVarMethod.setAccessible(true);

			pointExtCtor = pointExtClass.getDeclaredConstructors()[0];
			pointExtCtor.setAccessible(true);

			yField = pointExtClass.getDeclaredField("y");
			yField.setAccessible(true);
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | NoSuchFieldException | ClassNotFoundException e) {
			throw new RuntimeException("Can't initialize BouncyCastle25519 shim", e);
		}
	}

	private static int[] obtainYFromPublicKey(byte[] ed25519PublicKey) {
		try {
			Object pA = pointExtCtor.newInstance();

			Boolean result = (Boolean) decodePointVarMethod.invoke(null, ed25519PublicKey, 0, true, pA);
			if (result == null || !result)
				return null;

			return (int[]) yField.get(pA);
		} catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Can't reflect into BouncyCastle", e);
		}
	}

	public static byte[] toX25519PublicKey(byte[] ed25519PublicKey) {
		int[] one = new int[X25519Field.SIZE];
		X25519Field.one(one);

		int[] y = obtainYFromPublicKey(ed25519PublicKey);

		int[] oneMinusY = new int[X25519Field.SIZE];
		X25519Field.sub(one, y, oneMinusY);

		int[] onePlusY = new int[X25519Field.SIZE];
		X25519Field.add(one, y, onePlusY);

		int[] oneMinusYInverted = new int[X25519Field.SIZE];
		X25519Field.inv(oneMinusY, oneMinusYInverted);

		int[] u = new int[X25519Field.SIZE];
		X25519Field.mul(onePlusY, oneMinusYInverted, u);

		byte[] x25519PublicKey = new byte[X25519.SCALAR_SIZE];
		X25519Field.encode(u, x25519PublicKey, 0);

		return x25519PublicKey;
	}

	public static byte[] toX25519PrivateKey(byte[] ed25519PrivateKey) {
		Digest d = Ed25519.createPrehash();
		byte[] h = new byte[d.getDigestSize()];

		d.update(ed25519PrivateKey, 0, ed25519PrivateKey.length);
		d.doFinal(h, 0);

		byte[] s = new byte[X25519.SCALAR_SIZE];

		System.arraycopy(h, 0, s, 0, X25519.SCALAR_SIZE);
		s[0] &= 0xF8;
		s[X25519.SCALAR_SIZE - 1] &= 0x7F;
		s[X25519.SCALAR_SIZE - 1] |= 0x40;

		return s;
	}

}
