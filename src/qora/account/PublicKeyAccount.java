package qora.account;

import qora.crypto.Crypto;
import qora.crypto.Ed25519;

public class PublicKeyAccount extends Account {

	protected byte[] publicKey;

	public PublicKeyAccount(byte[] publicKey) {
		this.publicKey = publicKey;
		this.address = Crypto.toAddress(this.publicKey);
	}

	protected PublicKeyAccount() {
	}

	public byte[] getPublicKey() {
		return publicKey;
	}

	public boolean verify(byte[] signature, byte[] message) {
		try {
			return Ed25519.verify(signature, message, this.publicKey);
		} catch (Exception e) {
			return false;
		}
	}

}
