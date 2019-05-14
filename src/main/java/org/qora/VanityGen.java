package org.qora;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Random;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qora.account.PrivateKeyAccount;
import org.qora.utils.Base58;

public class VanityGen {

	public static void main(String argv[]) throws IOException {
		if (argv.length != 1) {
			System.err.println("Usage: Vanitygen <leading-chars>");
			System.err.println("Example: VanityGen Qcat");
			return;
		}

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		Random random = new SecureRandom();
		byte[] seed = new byte[32];

		while (true) {
			random.nextBytes(seed);
			PrivateKeyAccount account = new PrivateKeyAccount(null, seed);

			if (account.getAddress().startsWith(argv[0]))
				System.out.println(String.format("Address: %s, public key: %s, private key: %s", account.getAddress(), Base58.encode(account.getPublicKey()), Base58.encode(seed)));
		}
	}

}
