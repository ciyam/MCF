package org.qora.test;

import org.junit.Test;
import org.qora.block.BlockChain;
import org.qora.crypto.Crypto;

import static org.junit.Assert.*;

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
		String expected = BlockChain.getUseBrokenMD160ForAddresses() ? "QUD9y7NZqTtNwvSAUfewd7zKUGoVivVnTW" : "QPc6TvGJ5RjW6LpwUtafx7XRCdRvyN6rsA";

		assertEquals(expected, Crypto.toAddress(publicKey));
	}

}
