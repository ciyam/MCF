package org.qora.account;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.qora.crypto.Crypto;
import org.qora.data.account.AccountData;
import org.qora.repository.Repository;

public class PublicKeyAccount extends Account {

	protected final byte[] publicKey;
	protected final Ed25519PublicKeyParameters edPublicKeyParams;

	public PublicKeyAccount(Repository repository, byte[] publicKey) {
		this(repository, new Ed25519PublicKeyParameters(publicKey, 0));
	}

	protected PublicKeyAccount(Repository repository, Ed25519PublicKeyParameters edPublicKeyParams) {
		super(repository, Crypto.toAddress(edPublicKeyParams.getEncoded()));

		this.edPublicKeyParams = edPublicKeyParams;
		this.publicKey = edPublicKeyParams.getEncoded();
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	@Override
	protected AccountData buildAccountData() {
		AccountData accountData = super.buildAccountData();
		accountData.setPublicKey(this.publicKey);
		return accountData;
	}

	public boolean verify(byte[] signature, byte[] message) {
		return PublicKeyAccount.verify(this.publicKey, signature, message);
	}

	public static boolean verify(byte[] publicKey, byte[] signature, byte[] message) {
		try {
			return Ed25519.verify(signature, 0, publicKey, 0, message, 0, message.length);
		} catch (Exception e) {
			return false;
		}
	}

	public static String getAddress(byte[] publicKey) {
		return Crypto.toAddress(publicKey);
	}

}
