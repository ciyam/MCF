package org.qora.account;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.qora.crypto.BouncyCastle25519;
import org.qora.crypto.Crypto;
import org.qora.repository.Repository;

public class PrivateKeyAccount extends PublicKeyAccount {

	private static final int SIGNATURE_LENGTH = 64;
	private static final int SHARED_SECRET_LENGTH = 32;

	private final byte[] privateKey;
	private final Ed25519PrivateKeyParameters edPrivateKeyParams;

	/**
	 * Create PrivateKeyAccount using byte[32] seed.
	 * 
	 * @param seed
	 *            byte[32] used to create private/public key pair
	 * @throws IllegalArgumentException
	 *             if passed invalid seed
	 */
	public PrivateKeyAccount(Repository repository, byte[] seed) {
		this(repository, new Ed25519PrivateKeyParameters(seed, 0));
	}

	private PrivateKeyAccount(Repository repository, Ed25519PrivateKeyParameters edPrivateKeyParams) {
		this(repository, edPrivateKeyParams, edPrivateKeyParams.generatePublicKey());
	}

	private PrivateKeyAccount(Repository repository, Ed25519PrivateKeyParameters edPrivateKeyParams, Ed25519PublicKeyParameters edPublicKeyParams) {
		super(repository, edPublicKeyParams);

		this.privateKey = edPrivateKeyParams.getEncoded();
		this.edPrivateKeyParams = edPrivateKeyParams;
	}

	public byte[] getPrivateKey() {
		return this.privateKey;
	}

	public byte[] sign(byte[] message) {
		byte[] signature = new byte[SIGNATURE_LENGTH];

		this.edPrivateKeyParams.sign(Ed25519.Algorithm.Ed25519, edPublicKeyParams, null, message, 0, message.length, signature, 0);

		return signature;
	}

	public byte[] getSharedSecret(byte[] publicKey) {
		byte[] x25519PrivateKey = BouncyCastle25519.toX25519PrivateKey(this.privateKey);
		X25519PrivateKeyParameters xPrivateKeyParams = new X25519PrivateKeyParameters(x25519PrivateKey, 0);

		byte[] x25519PublicKey = BouncyCastle25519.toX25519PublicKey(publicKey);
		X25519PublicKeyParameters xPublicKeyParams = new X25519PublicKeyParameters(x25519PublicKey, 0);

		byte[] sharedSecret = new byte[SHARED_SECRET_LENGTH];
		xPrivateKeyParams.generateSecret(xPublicKeyParams, sharedSecret, 0);

		return sharedSecret;
	}

	public byte[] getProxyPrivateKey(byte[] publicKey) {
		byte[] sharedSecret = this.getSharedSecret(publicKey);

		return Crypto.digest(sharedSecret);
	}

}
