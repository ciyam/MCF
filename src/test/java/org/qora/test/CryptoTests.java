package org.qora.test;

import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockChain;
import org.qora.crypto.BouncyCastle25519;
import org.qora.crypto.Crypto;
import org.qora.test.common.Common;

import static org.junit.Assert.*;

import java.security.SecureRandom;

import org.bitcoinj.core.Base58;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import com.google.common.hash.HashCode;

public class CryptoTests extends Common {

	@Test
	public void testDigest() {
		byte[] input = HashCode.fromString("00").asBytes();
		byte[] digest = Crypto.digest(input);
		byte[] expected = HashCode.fromString("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d").asBytes();

		assertArrayEquals(expected, digest);
	}

	@Test
	public void testDoubleDigest() {
		byte[] input = HashCode.fromString("00").asBytes();
		byte[] digest = Crypto.doubleDigest(input);
		byte[] expected = HashCode.fromString("1406e05881e299367766d313e26c05564ec91bf721d31726bd6e46e60689539a").asBytes();

		assertArrayEquals(expected, digest);
	}

	@Test
	public void testPublicKeyToAddress() {
		byte[] publicKey = HashCode.fromString("775ada64a48a30b3bfc4f1db16bca512d4088704975a62bde78781ce0cba90d6").asBytes();
		String expected = BlockChain.getInstance().getUseBrokenMD160ForAddresses() ? "QUD9y7NZqTtNwvSAUfewd7zKUGoVivVnTW" : "QPc6TvGJ5RjW6LpwUtafx7XRCdRvyN6rsA";

		assertEquals(expected, Crypto.toAddress(publicKey));
	}

	@Test
	public void verifySignature() {
		final String privateKey58 = "A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6";
		final String message58 = "111FDmMy7u7ChH3SNLNYoUqE9eQRDVKGzhYTAU7XJRVZ7L966aKdDFBeD5WBQP372Lgpdbt4L8HuPobB1CWbJzdUqa72MYVA8A8pmocQQpzRsC5Kreif94yiScTDnnvCWcNERj9J2sqTH12gVdeeLt9Ery7HZFi6tDyysTLBkWfmDjuLnSfDKc7xeqZFkMSG1oatPedzrsDtrBZ";
		final String expectedSignature58 = "41g1hidZGbNn8xCCH41j1V1tD9iUwz7LCF4UcH19eindYyBnjKxfHdPm9qyRvLYFmXp8PV8YXzMXWUUngmqHo5Ho";

		final byte[] privateKey = Base58.decode(privateKey58);
		PrivateKeyAccount account = new PrivateKeyAccount(null, privateKey);

		byte[] message = Base58.decode(message58);
		byte[] signature = account.sign(message);
		assertEquals(expectedSignature58, Base58.encode(signature));

		assertTrue(account.verify(signature, message));
	}

	@Test
	public void testMassEd25519ToX25519() {
		// Lots of random tests just in case of leading sign bit issues
		SecureRandom random = new SecureRandom();

		for (int i = 0; i < 1000; ++i) {
			byte[] ed25519PrivateKey = new byte[32];
			random.nextBytes(ed25519PrivateKey);
			PrivateKeyAccount account = new PrivateKeyAccount(null, ed25519PrivateKey);

			byte[] x25519PrivateKey = BouncyCastle25519.toX25519PrivateKey(account.getPrivateKey());
			X25519PrivateKeyParameters x25519PrivateKeyParams = new X25519PrivateKeyParameters(x25519PrivateKey, 0);

			// Derive X25519 public key from X25519 private key
			byte[] x25519PublicKeyFromPrivate = x25519PrivateKeyParams.generatePublicKey().getEncoded();

			// Derive X25519 public key from Ed25519 public key
			byte[] x25519PublicKeyFromEd25519 = BouncyCastle25519.toX25519PublicKey(account.getPublicKey());

			assertEquals(String.format("Public keys do not match, from private key %s", Base58.encode(ed25519PrivateKey)), Base58.encode(x25519PublicKeyFromPrivate), Base58.encode(x25519PublicKeyFromEd25519));
		}
	}

	@Test
	public void testBCseed() {
		final String privateKey58 = "A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6";
		final String publicKey58 = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP";

		final byte[] privateKey = Base58.decode(privateKey58);
		PrivateKeyAccount account = new PrivateKeyAccount(null, privateKey);

		String expected58 = publicKey58;
		String actual58 = Base58.encode(account.getPublicKey());
		assertEquals("qora-core derived public key incorrect", expected58, actual58);

		Ed25519PrivateKeyParameters privateKeyParams = new Ed25519PrivateKeyParameters(privateKey, 0);
		Ed25519PublicKeyParameters publicKeyParams = privateKeyParams.generatePublicKey();

		actual58 = Base58.encode(publicKeyParams.getEncoded());
		assertEquals("BouncyCastle derived public key incorrect", expected58, actual58);

		final byte[] publicKey = Base58.decode(publicKey58);
		publicKeyParams = new Ed25519PublicKeyParameters(publicKey, 0);

		actual58 = Base58.encode(publicKeyParams.getEncoded());
		assertEquals("BouncyCastle decoded public key incorrect", expected58, actual58);
	}

	private static byte[] calcBCSharedSecret(byte[] ed25519PrivateKey, byte[] ed25519PublicKey) {
		byte[] x25519PrivateKey = BouncyCastle25519.toX25519PrivateKey(ed25519PrivateKey);
		X25519PrivateKeyParameters privateKeyParams = new X25519PrivateKeyParameters(x25519PrivateKey, 0);

		byte[] x25519PublicKey = BouncyCastle25519.toX25519PublicKey(ed25519PublicKey);
		X25519PublicKeyParameters publicKeyParams = new X25519PublicKeyParameters(x25519PublicKey, 0);

		byte[] sharedSecret = new byte[32];

		X25519Agreement keyAgree = new X25519Agreement();
		keyAgree.init(privateKeyParams);
		keyAgree.calculateAgreement(publicKeyParams, sharedSecret, 0);

		return sharedSecret;
	}

	@Test
	public void testBCSharedSecret() {
		final byte[] ourPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
		final byte[] theirPublicKey = Base58.decode("C6wuddsBV3HzRrXUtezE7P5MoRXp5m3mEDokRDGZB6ry");

		final String expectedOurX25519PrivateKey = "HBPAUyWkrHt41s1a7yd6m7d1VswzLs4p9ob6AsqUQSCh";
		final String expectedTheirX25519PublicKey = "ANjnZLRSzW9B1aVamiYGKP3XtBooU9tGGDjUiibUfzp2";
		final String expectedSharedSecret = "DTMZYG96x8XZuGzDvHFByVLsXedimqtjiXHhXPVe58Ap";

		byte[] ourX25519PrivateKey = BouncyCastle25519.toX25519PrivateKey(ourPrivateKey);
		assertEquals("X25519 private key incorrect", expectedOurX25519PrivateKey, Base58.encode(ourX25519PrivateKey));

		byte[] theirX25519PublicKey = BouncyCastle25519.toX25519PublicKey(theirPublicKey);
		assertEquals("X25519 public key incorrect", expectedTheirX25519PublicKey, Base58.encode(theirX25519PublicKey));

		byte[] sharedSecret = calcBCSharedSecret(ourPrivateKey, theirPublicKey);

		assertEquals("shared secret incorrect", expectedSharedSecret, Base58.encode(sharedSecret));
	}

	@Test
	public void testSharedSecret() {
		final byte[] ourPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
		final byte[] theirPublicKey = Base58.decode("C6wuddsBV3HzRrXUtezE7P5MoRXp5m3mEDokRDGZB6ry");
		final String expectedSharedSecret = "DTMZYG96x8XZuGzDvHFByVLsXedimqtjiXHhXPVe58Ap";

		PrivateKeyAccount generator = new PrivateKeyAccount(null, ourPrivateKey);

		byte[] sharedSecret = generator.getSharedSecret(theirPublicKey);

		assertEquals("shared secret incorrect", expectedSharedSecret, Base58.encode(sharedSecret));
	}

	@Test
	public void testSharedSecretMatchesBC() {
		final byte[] ourPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
		final byte[] theirPublicKey = Base58.decode("C6wuddsBV3HzRrXUtezE7P5MoRXp5m3mEDokRDGZB6ry");
		final String expectedSharedSecret = "DTMZYG96x8XZuGzDvHFByVLsXedimqtjiXHhXPVe58Ap";

		PrivateKeyAccount generator = new PrivateKeyAccount(null, ourPrivateKey);

		byte[] ourSharedSecret = generator.getSharedSecret(theirPublicKey);

		assertEquals("shared secret incorrect", expectedSharedSecret, Base58.encode(ourSharedSecret));

		byte[] bcSharedSecret = calcBCSharedSecret(ourPrivateKey, theirPublicKey);

		assertEquals("shared secrets do not match", Base58.encode(ourSharedSecret), Base58.encode(bcSharedSecret));
	}

	@Test
	public void testRandomBCSharedSecret2() {
		// Check shared secret is the same generated from either set of private/public keys
		SecureRandom random = new SecureRandom();

		X25519PrivateKeyParameters ourPrivateKeyParams = new X25519PrivateKeyParameters(random);
		X25519PrivateKeyParameters theirPrivateKeyParams = new X25519PrivateKeyParameters(random);

		X25519PublicKeyParameters ourPublicKeyParams = ourPrivateKeyParams.generatePublicKey();
		X25519PublicKeyParameters theirPublicKeyParams = theirPrivateKeyParams.generatePublicKey();

		byte[] ourSharedSecret = new byte[32];

		X25519Agreement keyAgree = new X25519Agreement();
		keyAgree.init(ourPrivateKeyParams);
		keyAgree.calculateAgreement(theirPublicKeyParams, ourSharedSecret, 0);

		byte[] theirSharedSecret = new byte[32];

		keyAgree = new X25519Agreement();
		keyAgree.init(theirPrivateKeyParams);
		keyAgree.calculateAgreement(ourPublicKeyParams, theirSharedSecret, 0);

		assertEquals("shared secrets do not match", Base58.encode(ourSharedSecret), Base58.encode(theirSharedSecret));
	}

	@Test
	public void testBCSharedSecret2() {
		// Check shared secret is the same generated from either set of private/public keys
		final byte[] ourPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
		final byte[] ourPublicKey = Base58.decode("2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP");

		final byte[] theirPrivateKey = Base58.decode("AdTd9SUEYSdTW8mgK3Gu72K97bCHGdUwi2VvLNjUohot");
		final byte[] theirPublicKey = Base58.decode("C6wuddsBV3HzRrXUtezE7P5MoRXp5m3mEDokRDGZB6ry");

		byte[] ourSharedSecret = calcBCSharedSecret(ourPrivateKey, theirPublicKey);

		byte[] theirSharedSecret = calcBCSharedSecret(theirPrivateKey, ourPublicKey);

		assertEquals("shared secrets do not match", Base58.encode(ourSharedSecret), Base58.encode(theirSharedSecret));
	}

	@Test
	public void testMassRandomBCSharedSecrets() {
		// Lots of random shared secret tests just in case of leading sign bit issues
		SecureRandom random = new SecureRandom();

		for (int i = 0; i < 1000; ++i) {
			byte[] ourPrivateKey = new byte[32];
			random.nextBytes(ourPrivateKey);
			PrivateKeyAccount ourAccount = new PrivateKeyAccount(null, ourPrivateKey);

			byte[] theirPrivateKey = new byte[32];
			random.nextBytes(theirPrivateKey);
			PrivateKeyAccount theirAccount = new PrivateKeyAccount(null, theirPrivateKey);

			byte[] ourSharedSecret = calcBCSharedSecret(ourPrivateKey, theirAccount.getPublicKey());

			byte[] theirSharedSecret = calcBCSharedSecret(theirPrivateKey, ourAccount.getPublicKey());

			assertEquals("#" + i + " shared secrets do not match", Base58.encode(ourSharedSecret), Base58.encode(theirSharedSecret));
		}
	}

	@Test
	public void testProxyKeys() {
		final byte[] ourPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
		final byte[] theirPublicKey = Base58.decode("C6wuddsBV3HzRrXUtezE7P5MoRXp5m3mEDokRDGZB6ry");

		final String expectedProxyPrivateKey = "6KszntmNuXmpUkzLfuttgMPeownctxrnyZUG9rErKJJx";

		PrivateKeyAccount mintingAccount = new PrivateKeyAccount(null, ourPrivateKey);
		byte[] proxyPrivateKey = mintingAccount.getProxyPrivateKey(theirPublicKey);

		assertEquals(expectedProxyPrivateKey, Base58.encode(proxyPrivateKey));
	}

}
