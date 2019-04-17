package org.qora.test;

import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.BlockChain;
import org.qora.crypto.Crypto;
import org.qora.test.common.Common;

import static org.junit.Assert.*;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

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
	public void testBCseed() throws NoSuchAlgorithmException, NoSuchProviderException {
		final String privateKey58 = "A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6";
		final String publicKey58 = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP";

		final byte[] privateKey = Base58.decode(privateKey58);
		PrivateKeyAccount account = new PrivateKeyAccount(null, privateKey);

		String expected58 = publicKey58;
		String actual58 = Base58.encode(account.getPublicKey());
		assertEquals("qora-core generated public key incorrect", expected58, actual58);

		Ed25519PrivateKeyParameters privateKeyParams = new Ed25519PrivateKeyParameters(privateKey, 0);
		Ed25519PublicKeyParameters publicKeyParams = privateKeyParams.generatePublicKey();

		actual58 = Base58.encode(publicKeyParams.getEncoded());
		assertEquals("BouncyCastle generated public key incorrect", expected58, actual58);
	}

	@Test
	public void testBCSharedSecret() throws NoSuchAlgorithmException, NoSuchProviderException {
		final byte[] ourPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
		final byte[] theirPublicKey = Base58.decode("2sbcMmVKke5inS4yrbeoG6Cyw2mZCptQNjyWgnY4YHaF");
		final String expectedProxyPrivateKey = "EZhKy6wEh1ncQsvx6x3yV2sqjjsoU1bTTqrMcFLjLmp4";

		X25519PrivateKeyParameters ourPrivateKeyParams = new X25519PrivateKeyParameters(ourPrivateKey, 0);
		X25519PublicKeyParameters theirPublicKeyParams = new X25519PublicKeyParameters(theirPublicKey, 0);

		byte[] sharedSecret = new byte[32];

		X25519Agreement keyAgree = new X25519Agreement();
		keyAgree.init(ourPrivateKeyParams);
		keyAgree.calculateAgreement(theirPublicKeyParams, sharedSecret, 0);

		String proxyPrivateKey = Base58.encode(Crypto.digest(sharedSecret));

		assertEquals("proxy private key incorrect", expectedProxyPrivateKey, proxyPrivateKey);
	}

	@Test
	public void testSharedSecret() throws NoSuchAlgorithmException, NoSuchProviderException {
		final byte[] ourPrivateKey = Base58.decode("A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6");
		final byte[] theirPublicKey = Base58.decode("2sbcMmVKke5inS4yrbeoG6Cyw2mZCptQNjyWgnY4YHaF");
		final String expectedProxyPrivateKey = "EZhKy6wEh1ncQsvx6x3yV2sqjjsoU1bTTqrMcFLjLmp4";

		PrivateKeyAccount generator = new PrivateKeyAccount(null, ourPrivateKey);

		byte[] sharedSecret = generator.getSharedSecret(theirPublicKey);

		String proxyPrivateKey = Base58.encode(Crypto.digest(sharedSecret));

		assertEquals("proxy private key incorrect", expectedProxyPrivateKey, proxyPrivateKey);
	}

}
