package org.qora.network;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashSet;

import org.qora.network.message.ProofMessage;

import com.google.common.primitives.Longs;

public class Proof extends Thread {

	private static final int MIN_PROOF_ZEROS = 2;
	private static final MessageDigest sha256;
	static {
		try {
			sha256 = MessageDigest.getInstance("SHA256");
		} catch (NoSuchAlgorithmException e) {
			// Can't progress
			throw new RuntimeException("Message digest SHA256 not available");
		}
	}

	private static final HashSet<Long> seenSalts = new HashSet<>();

	private Peer peer;

	public Proof(Peer peer) {
		this.peer = peer;
		setDaemon(true);
	}

	public static boolean seenSalt(long salt) {
		synchronized (seenSalts) {
			return seenSalts.contains(salt);
		}
	}

	public static void addSalt(long salt) {
		synchronized (seenSalts) {
			seenSalts.add(salt);
		}
	}

	@Override
	public void run() {
		setName("Proof for peer " + this.peer.getRemoteSocketAddress());

		// Do proof-of-work calculation to gain acceptance with remote end

		// Remote end knows this (approximately)
		long timestamp = this.peer.getConnectionTimestamp();

		// Needs to be unique on the remote end
		long salt = new SecureRandom().nextLong();

		byte[] message = new byte[8 + 8 + 8]; // nonce + salt + timestamp

		byte[] saltBytes = Longs.toByteArray(salt);
		System.arraycopy(saltBytes, 0, message, 8, saltBytes.length);

		byte[] timestampBytes = Longs.toByteArray(timestamp);
		System.arraycopy(timestampBytes, 0, message, 8 + 8, timestampBytes.length);

		long nonce;
		for (nonce = 0; nonce < Long.MAX_VALUE; ++nonce) {
			// Check whether we're shutting down every so often
			if ((nonce & 0xff) == 0 && Thread.currentThread().isInterrupted())
				// throw new InterruptedException("Interrupted during peer proof calculation");
				return;

			byte[] nonceBytes = Longs.toByteArray(nonce);
			System.arraycopy(nonceBytes, 0, message, 0, nonceBytes.length);

			byte[] digest = sha256.digest(message);

			if (check(digest))
				break;
		}

		ProofMessage proofMessage = new ProofMessage(timestamp, salt, nonce);
		peer.sendMessage(proofMessage);
	}

	private static boolean check(byte[] digest) {
		int idx;
		for (idx = 0; idx < MIN_PROOF_ZEROS; ++idx)
			if (digest[idx] != 0)
				break;

		return idx == MIN_PROOF_ZEROS;
	}

	public static boolean check(long timestamp, long salt, long nonce) {
		byte[] message = new byte[8 + 8 + 8];

		byte[] saltBytes = Longs.toByteArray(salt);
		System.arraycopy(saltBytes, 0, message, 8, saltBytes.length);

		byte[] timestampBytes = Longs.toByteArray(timestamp);
		System.arraycopy(timestampBytes, 0, message, 8 + 8, timestampBytes.length);

		byte[] nonceBytes = Longs.toByteArray(nonce);
		System.arraycopy(nonceBytes, 0, message, 0, nonceBytes.length);

		byte[] digest = sha256.digest(message);

		return check(digest);
	}

}
