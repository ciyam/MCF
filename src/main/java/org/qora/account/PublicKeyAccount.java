package org.qora.account;

import org.qora.crypto.Crypto;
import org.qora.crypto.Ed25519;
import org.qora.repository.Repository;

public class PublicKeyAccount extends Account {

	public PublicKeyAccount(Repository repository, byte[] publicKey) {
		super(repository, Crypto.toAddress(publicKey));

		this.accountData.setPublicKey(publicKey);
	}

	protected PublicKeyAccount() {
	}

	public byte[] getPublicKey() {
		return this.accountData.getPublicKey();
	}

	public boolean verify(byte[] signature, byte[] message) {
		return PublicKeyAccount.verify(this.accountData.getPublicKey(), signature, message);
	}

	public static boolean verify(byte[] publicKey, byte[] signature, byte[] message) {
		try {
			return Ed25519.verify(signature, message, publicKey);
		} catch (Exception e) {
			return false;
		}
	}

	public static String getAddress(byte[] publicKey) {
		return Crypto.toAddress(publicKey);
	}

}
