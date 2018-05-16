package qora.account;

import qora.crypto.Crypto;
import utils.Pair;

public class PrivateKeyAccount extends PublicKeyAccount {

	private byte[] seed;
	private Pair<byte[], byte[]> keyPair;

	public PrivateKeyAccount(byte[] seed) {
		this.seed = seed;
		this.keyPair = Crypto.createKeyPair(seed);
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

}
