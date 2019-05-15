package org.qora;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Random;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qora.account.PrivateKeyAccount;
import org.qora.crypto.Crypto;
import org.qora.utils.BIP39;
import org.qora.utils.Base58;

import com.google.common.primitives.Bytes;

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
		byte[] entropy = new byte[16];

		while (true) {
			// Generate entropy internally
			random.nextBytes(entropy);

			// Use SHA256 to generate more bits
			byte[] hash = Crypto.digest(entropy);

			// Append first 4 bits from hash to end. (Actually 8 bits but we only use 4).
			byte checksum = (byte) (hash[0] & 0xf0);
			byte[] entropy132 = Bytes.concat(entropy, new byte[] { checksum });

			String mnemonic = BIP39.encode(entropy132, "en");

			PrivateKeyAccount account = new PrivateKeyAccount(null, hash);

			if (account.getAddress().startsWith(argv[0]))
				System.out.println(String.format("Address: %s, public key: %s, private key: %s, mnemonic: %s", account.getAddress(), Base58.encode(account.getPublicKey()), Base58.encode(hash), mnemonic));
		}
	}

}
