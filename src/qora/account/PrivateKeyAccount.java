package qora.account;

import data.account.AccountData;
import qora.crypto.Crypto;
import qora.crypto.Ed25519;
import repository.Repository;
import utils.Pair;

public class PrivateKeyAccount extends PublicKeyAccount {

	private byte[] seed;
	private Pair<byte[], byte[]> keyPair;

	/**
	 * Create PrivateKeyAccount using byte[32] seed.
	 * 
	 * @param seed
	 *            byte[32] used to create private/public key pair
	 */
	public PrivateKeyAccount(Repository repository, byte[] seed) {
		this.repository = repository;
		this.seed = seed;
		this.keyPair = Ed25519.createKeyPair(seed);
		this.publicKey = keyPair.getB();
		this.accountData = new AccountData(Crypto.toAddress(this.publicKey));
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
