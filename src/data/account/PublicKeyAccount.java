package data.account;

import qora.crypto.Crypto;

public class PublicKeyAccount extends Account {

	// Properties
	protected byte[] publicKey;

	// Constructors

	public PublicKeyAccount(byte[] publicKey) {
		super(Crypto.toAddress(publicKey));

		this.publicKey = publicKey;
	}

	protected PublicKeyAccount() {
	}

	// Getters/Setters

	public byte[] getPublicKey() {
		return this.publicKey;
	}

}
