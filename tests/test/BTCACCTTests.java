package test;

import java.io.File;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.WalletTransaction.Pool;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Bytes;

/**
 * Initiator must be Qora-chain so that initiator can send initial message to BTC P2SH then Qora can scan for P2SH add send corresponding message to Qora AT.
 *
 * Initiator (wants Qora, has BTC)
 * 		Funds BTC P2SH address
 * 
 * Responder (has Qora, wants BTC)
 * 		Builds Qora ACCT AT and funds it with Qora
 * 
 * Initiator sends recipient+secret+script as input to BTC P2SH address, releasing BTC amount - fees to responder
 * 
 * Qora nodes scan for P2SH output, checks amount and recipient and if ok sends secret to Qora ACCT AT
 * (Or it's possible to feed BTC transaction details into Qora AT so it can check them itself?) 
 * 
 * Qora ACCT AT sends its Qora to initiator
 *
 */

public class BTCACCTTests {

	private static final long TIMEOUT = 600L;
	private static final Coin sendValue = Coin.valueOf(6_000L);
	private static final Coin fee = Coin.valueOf(2_000L);

	private static final byte[] senderPrivKeyBytes = HashCode.fromString("027fb5828c5e201eaf6de4cd3b0b340d16a191ef848cd691f35ef8f727358c9c").asBytes();
	private static final byte[] recipientPrivKeyBytes = HashCode.fromString("ec199a4abc9d3bf024349e397535dfee9d287e174aeabae94237eb03a0118c03").asBytes();

	// The following need to be updated manually
	private static final String prevTxHash = "70ee97f20afea916c2e7b47f6abf3c75f97c4c2251b4625419406a2dd47d16b5";
	private static final Coin prevTxBalance = Coin.valueOf(562_000L); // This is NOT the amount but the unspent balance
	private static final long prevTxOutputIndex = 1L;

	// For when we want to re-run
	private static final byte[] prevSecret = HashCode.fromString("30a13291e350214bea5318f990b77bc11d2cb709f7c39859f248bef396961dcc").asBytes();
	private static final long prevLockTime = 1539347892L;
	private static final boolean usePreviousFundingTx = true;

	private static final boolean doRefundNotRedeem = false;

	@BeforeClass
	public static void beforeClass() {
		Security.insertProviderAt(new BouncyCastleProvider(), 0);
	}

	@Test
	public void buildBTCACCTTest() throws NoSuchAlgorithmException, InsufficientMoneyException, InterruptedException, ExecutionException, UnknownHostException {
		byte[] secret = new byte[32];
		new SecureRandom().nextBytes(secret);

		if (usePreviousFundingTx)
			secret = prevSecret;

		System.out.println("Secret: " + HashCode.fromBytes(secret).toString());

		MessageDigest sha256Digester = MessageDigest.getInstance("SHA-256");

		byte[] secretHash = sha256Digester.digest(secret);
		String secretHashHex = HashCode.fromBytes(secretHash).toString();

		System.out.println("SHA256(secret): " + secretHashHex);

		NetworkParameters params = TestNet3Params.get();
		// NetworkParameters params = RegTestParams.get();
		System.out.println("Network: " + params.getId());

		WalletAppKit kit = new WalletAppKit(params, new File("."), "btc-tests");

		kit.setBlockingStartup(false);
		kit.startAsync();
		kit.awaitRunning();

		long now = System.currentTimeMillis() / 1000L;
		long lockTime = now + TIMEOUT;

		if (usePreviousFundingTx)
			lockTime = prevLockTime;

		System.out.println("LockTime: " + lockTime);

		ECKey senderKey = ECKey.fromPrivate(senderPrivKeyBytes);
		kit.wallet().importKey(senderKey);
		ECKey recipientKey = ECKey.fromPrivate(recipientPrivKeyBytes);
		kit.wallet().importKey(recipientKey);

		byte[] senderPubKey = senderKey.getPubKey();
		System.out.println("Sender address: " + senderKey.toAddress(params).toBase58());
		System.out.println("Sender pubkey: " + HashCode.fromBytes(senderPubKey).toString());

		byte[] recipientPubKey = recipientKey.getPubKey();
		System.out.println("Recipient address: " + recipientKey.toAddress(params).toBase58());
		System.out.println("Recipient pubkey: " + HashCode.fromBytes(recipientPubKey).toString());

		byte[] redeemScriptBytes = buildRedeemScript(secret, senderPubKey, recipientPubKey, lockTime);
		System.out.println("Redeem script: " + HashCode.fromBytes(redeemScriptBytes).toString());

		byte[] redeemScriptHash = hash160(redeemScriptBytes);

		Address p2shAddress = Address.fromP2SHHash(params, redeemScriptHash);
		System.out.println("P2SH address: " + p2shAddress.toBase58());

		// Send amount to P2SH address
		Transaction fundingTransaction = buildFundingTransaction(params, Sha256Hash.wrap(prevTxHash), prevTxOutputIndex, prevTxBalance, senderKey,
				sendValue.add(fee), redeemScriptHash);

		System.out.println("Sending " + sendValue.add(fee).toPlainString() + " to " + p2shAddress.toBase58());
		if (!usePreviousFundingTx)
			broadcastWithConfirmation(kit, fundingTransaction);

		if (doRefundNotRedeem) {
			// Refund
			System.out.println("Refunding " + sendValue.toPlainString() + " back to " + senderKey.toAddress(params));

			now = System.currentTimeMillis() / 1000L;
			long refundLockTime = now - 60 * 30; // 30 minutes in the past, needs to before 'now' and before "median block time" (median of previous 11 block
													// timestamps)
			if (refundLockTime < lockTime)
				throw new RuntimeException("Too soon to refund");

			TransactionOutPoint fundingOutPoint = new TransactionOutPoint(params, 0, fundingTransaction);
			Transaction refundTransaction = buildRefundTransaction(params, fundingOutPoint, senderKey, sendValue, redeemScriptBytes, refundLockTime);
			broadcastWithConfirmation(kit, refundTransaction);
		} else {
			// Redeem
			System.out.println("Redeeming " + sendValue.toPlainString() + " to " + recipientKey.toAddress(params));

			TransactionOutPoint fundingOutPoint = new TransactionOutPoint(params, 0, fundingTransaction);
			Transaction redeemTransaction = buildRedeemTransaction(params, fundingOutPoint, recipientKey, sendValue, secret, redeemScriptBytes);
			broadcastWithConfirmation(kit, redeemTransaction);
		}

		kit.wallet().cleanup();

		for (Transaction transaction : kit.wallet().getTransactionPool(Pool.PENDING).values())
			System.out.println("Pending tx: " + transaction.getHashAsString());
	}

	private static final byte[] redeemScript1 = HashCode.fromString("76a820").asBytes();
	private static final byte[] redeemScript2 = HashCode.fromString("87637576a914").asBytes();
	private static final byte[] redeemScript3 = HashCode.fromString("88ac6704").asBytes();
	private static final byte[] redeemScript4 = HashCode.fromString("b17576a914").asBytes();
	private static final byte[] redeemScript5 = HashCode.fromString("88ac68").asBytes();

	private byte[] buildRedeemScript(byte[] secret, byte[] senderPubKey, byte[] recipientPubKey, long lockTime) {
		try {
			MessageDigest sha256Digester = MessageDigest.getInstance("SHA-256");

			byte[] secretHash = sha256Digester.digest(secret);
			byte[] senderPubKeyHash = hash160(senderPubKey);
			byte[] recipientPubKeyHash = hash160(recipientPubKey);

			return Bytes.concat(redeemScript1, secretHash, redeemScript2, recipientPubKeyHash, redeemScript3, toLEByteArray((int) (lockTime & 0xffffffffL)),
					redeemScript4, senderPubKeyHash, redeemScript5);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Message digest unsupported", e);
		}
	}

	private byte[] hash160(byte[] input) {
		try {
			MessageDigest rmd160Digester = MessageDigest.getInstance("RIPEMD160");
			MessageDigest sha256Digester = MessageDigest.getInstance("SHA-256");

			return rmd160Digester.digest(sha256Digester.digest(input));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Message digest unsupported", e);
		}
	}

	private Transaction buildFundingTransaction(NetworkParameters params, Sha256Hash prevTxHash, long outputIndex, Coin balance, ECKey sigKey, Coin value,
			byte[] redeemScriptHash) {
		Transaction fundingTransaction = new Transaction(params);

		// Outputs (needed before input so inputs can be signed)
		// Fixed amount to P2SH
		fundingTransaction.addOutput(value, ScriptBuilder.createP2SHOutputScript(redeemScriptHash));
		// Change to sender
		fundingTransaction.addOutput(balance.minus(value).minus(fee), ScriptBuilder.createOutputScript(sigKey.toAddress(params)));

		// Input
		// We create fake "to address" scriptPubKey for prev tx so our spending input is P2PKH type
		Script fakeScriptPubKey = ScriptBuilder.createOutputScript(sigKey.toAddress(params));
		TransactionOutPoint prevOut = new TransactionOutPoint(params, outputIndex, prevTxHash);
		fundingTransaction.addSignedInput(prevOut, fakeScriptPubKey, sigKey);

		return fundingTransaction;
	}

	private Transaction buildRedeemTransaction(NetworkParameters params, TransactionOutPoint fundingOutPoint, ECKey recipientKey, Coin value, byte[] secret,
			byte[] redeemScriptBytes) {
		Transaction redeemTransaction = new Transaction(params);
		redeemTransaction.setVersion(2);

		// Outputs
		redeemTransaction.addOutput(value, ScriptBuilder.createOutputScript(recipientKey.toAddress(params)));

		// Input
		byte[] recipientPubKey = recipientKey.getPubKey();
		ScriptBuilder scriptBuilder = new ScriptBuilder();
		scriptBuilder.addChunk(new ScriptChunk(recipientPubKey.length, recipientPubKey));
		scriptBuilder.addChunk(new ScriptChunk(secret.length, secret));
		scriptBuilder.addChunk(new ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, redeemScriptBytes));
		byte[] scriptPubKey = scriptBuilder.build().getProgram();

		TransactionInput input = new TransactionInput(params, null, scriptPubKey, fundingOutPoint);
		input.setSequenceNumber(0xffffffffL); // Final
		redeemTransaction.addInput(input);

		// Generate transaction signature for input
		boolean anyoneCanPay = false;
		Sha256Hash hash = redeemTransaction.hashForSignature(0, redeemScriptBytes, SigHash.ALL, anyoneCanPay);
		System.out.println("redeem transaction's input hash: " + hash.toString());

		ECKey.ECDSASignature ecSig = recipientKey.sign(hash);
		TransactionSignature txSig = new TransactionSignature(ecSig, SigHash.ALL, anyoneCanPay);
		byte[] txSigBytes = txSig.encodeToBitcoin();
		System.out.println("redeem transaction's signature: " + HashCode.fromBytes(txSigBytes).toString());

		// Prepend signature to input
		scriptBuilder.addChunk(0, new ScriptChunk(txSigBytes.length, txSigBytes));
		input.setScriptSig(scriptBuilder.build());

		return redeemTransaction;
	}

	private Transaction buildRefundTransaction(NetworkParameters params, TransactionOutPoint fundingOutPoint, ECKey senderKey, Coin value,
			byte[] redeemScriptBytes, long lockTime) {
		Transaction refundTransaction = new Transaction(params);
		refundTransaction.setVersion(2);

		// Outputs
		refundTransaction.addOutput(value, ScriptBuilder.createOutputScript(senderKey.toAddress(params)));

		// Input
		byte[] recipientPubKey = senderKey.getPubKey();
		ScriptBuilder scriptBuilder = new ScriptBuilder();
		scriptBuilder.addChunk(new ScriptChunk(recipientPubKey.length, recipientPubKey));
		scriptBuilder.addChunk(new ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, redeemScriptBytes));
		byte[] scriptPubKey = scriptBuilder.build().getProgram();

		TransactionInput input = new TransactionInput(params, null, scriptPubKey, fundingOutPoint);
		input.setSequenceNumber(0);
		refundTransaction.addInput(input);

		// Set locktime after input but before input signature is generated
		refundTransaction.setLockTime(lockTime);

		// Generate transaction signature for input
		boolean anyoneCanPay = false;
		Sha256Hash hash = refundTransaction.hashForSignature(0, redeemScriptBytes, SigHash.ALL, anyoneCanPay);
		System.out.println("refund transaction's input hash: " + hash.toString());

		ECKey.ECDSASignature ecSig = senderKey.sign(hash);
		TransactionSignature txSig = new TransactionSignature(ecSig, SigHash.ALL, anyoneCanPay);
		byte[] txSigBytes = txSig.encodeToBitcoin();
		System.out.println("refund transaction's signature: " + HashCode.fromBytes(txSigBytes).toString());

		// Prepend signature to input
		scriptBuilder.addChunk(0, new ScriptChunk(txSigBytes.length, txSigBytes));
		input.setScriptSig(scriptBuilder.build());

		return refundTransaction;
	}

	private void broadcastWithConfirmation(WalletAppKit kit, Transaction transaction) {
		System.out.println("Broadcasting tx: " + transaction.getHashAsString());
		System.out.println("TX hex: " + HashCode.fromBytes(transaction.bitcoinSerialize()).toString());

		System.out.println("Number of connected peers: " + kit.peerGroup().numConnectedPeers());
		TransactionBroadcast txBroadcast = kit.peerGroup().broadcastTransaction(transaction);

		try {
			txBroadcast.future().get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException("Transaction broadcast failed", e);
		}

		// wait for confirmation
		System.out.println("Waiting for confirmation of tx: " + transaction.getHashAsString());

		try {
			transaction.getConfidence().getDepthFuture(1).get();
		} catch (CancellationException | ExecutionException | InterruptedException e) {
			throw new RuntimeException("Transaction confirmation failed", e);
		}

		System.out.println("Confirmed tx: " + transaction.getHashAsString());
	}

	/** Convert int to little-endian byte array */
	private byte[] toLEByteArray(int value) {
		return new byte[] { (byte) (value), (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24) };
	}

}
