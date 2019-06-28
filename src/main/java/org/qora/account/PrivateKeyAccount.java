package org.qora.account;

import org.qora.crypto.Crypto;
import org.qora.crypto.Ed25519;
import org.qora.data.account.AccountData;
import org.qora.repository.Repository;
import org.qora.utils.Pair;

public class PrivateKeyAccount extends PublicKeyAccount {

	private byte[] seed;
	private Pair<byte[], byte[]> keyPair;

	/**
	 * Create PrivateKeyAccount using byte[32] seed.
	 * 
	 * @param seed
	 *            byte[32] used to create private/public key pair
	 * @throws IllegalArgumentException if passed invalid seed
	 */
	public PrivateKeyAccount(Repository repository, byte[] seed) {
		this.repository = repository;
		this.seed = seed;
		this.keyPair = Ed25519.createKeyPair(seed);

		byte[] publicKey = keyPair.getB();
		this.accountData = new AccountData(Crypto.toAddress(publicKey), null, publicKey);
	}

	public byte[] getSeed() {
		return this.seed;
	}

	public byte[] getPrivateKey() {
		return this.keyPair.getA();
	}

	public Pair<byte[], byte[]> getKeyPair() {
		return this.keyPair;
	}

	public byte[] sign(byte[] message) {
		try {
			return Ed25519.sign(this.keyPair, message);
		} catch (Exception e) {
			return null;
		}
	}

}
