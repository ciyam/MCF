package qora.account;

import qora.crypto.Crypto;
import qora.crypto.Ed25519;
import utils.Pair;

public class PrivateKeyAccount extends PublicKeyAccount {

	private byte[] seed;
	private Pair<byte[], byte[]> keyPair;

	public PrivateKeyAccount(byte[] seed) {
		this.seed = seed;
		this.keyPair = Ed25519.createKeyPair(seed);
		this.publicKey = keyPair.getB();
		this.address = Crypto.toAddress(this.publicKey);
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
