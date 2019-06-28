package org.qora.block;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.qora.account.GenesisAccount;
import org.qora.crypto.Crypto;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.GenesisTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.settings.Settings;
import org.qora.transaction.Transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

public class GenesisBlock extends Block {

	private static final Logger LOGGER = LogManager.getLogger(GenesisBlock.class);

	private static final byte[] GENESIS_REFERENCE = new byte[] {
		1, 1, 1, 1, 1, 1, 1, 1
	}; // NOTE: Neither 64 nor 128 bytes!
	private static final byte[] GENESIS_GENERATOR_PUBLIC_KEY = GenesisAccount.PUBLIC_KEY; // NOTE: 8 bytes not 32 bytes!

	// Properties
	private static BlockData blockData;
	private static List<TransactionData> transactionsData;

	// Constructors

	private GenesisBlock(Repository repository, BlockData blockData, List<TransactionData> transactions) throws DataException {
		super(repository, blockData, transactions, Collections.emptyList());
	}

	public static GenesisBlock getInstance(Repository repository) throws DataException {
		return new GenesisBlock(repository, blockData, transactionsData);
	}

	// Construction from JSON

	public static void fromJSON(JSONObject json) {
		// Version
		int version = 1; // but could be bumped later

		// Timestamp
		String timestampStr = (String) Settings.getTypedJson(json, "timestamp", String.class);
		long timestamp;

		if (timestampStr.equals("now"))
			timestamp = System.currentTimeMillis();
		else
			try {
				timestamp = Long.parseUnsignedLong(timestampStr);
			} catch (NumberFormatException e) {
				LOGGER.error("Unable to parse genesis timestamp: " + timestampStr);
				throw new RuntimeException("Unable to parse genesis timestamp");
			}

		// Transactions
		JSONArray transactionsJson = (JSONArray) Settings.getTypedJson(json, "transactions", JSONArray.class);
		List<TransactionData> transactions = new ArrayList<>();

		for (Object transactionObj : transactionsJson) {
			if (!(transactionObj instanceof JSONObject)) {
				LOGGER.error("Genesis transaction malformed in blockchain config file");
				throw new RuntimeException("Genesis transaction malformed in blockchain config file");
			}

			JSONObject transactionJson = (JSONObject) transactionObj;

			String recipient = (String) Settings.getTypedJson(transactionJson, "recipient", String.class);
			BigDecimal amount = Settings.getJsonBigDecimal(transactionJson, "amount");

			// assetId is optional
			if (transactionJson.containsKey("assetId")) {
				long assetId = (Long) Settings.getTypedJson(transactionJson, "assetId", Long.class);

				// We're into version 4 genesis block territory now
				version = 4;

				transactions.add(new GenesisTransactionData(recipient, amount, assetId, timestamp));
			} else {
				transactions.add(new GenesisTransactionData(recipient, amount, timestamp));
			}
		}

		// Generating balance
		BigDecimal generatingBalance = Settings.getJsonBigDecimal(json, "generatingBalance");

		byte[] reference = GENESIS_REFERENCE;
		int transactionCount = transactions.size();
		BigDecimal totalFees = BigDecimal.ZERO.setScale(8);
		byte[] generatorPublicKey = GENESIS_GENERATOR_PUBLIC_KEY;
		byte[] bytesForSignature = getBytesForSignature(version, reference, generatingBalance, generatorPublicKey);
		byte[] generatorSignature = calcSignature(bytesForSignature);
		byte[] transactionsSignature = generatorSignature;
		int height = 1;
		int atCount = 0;
		BigDecimal atFees = BigDecimal.ZERO.setScale(8);

		blockData = new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
				generatorPublicKey, generatorSignature, atCount, atFees);
		transactionsData = transactions;
	}

	// More information

	public static boolean isGenesisBlock(BlockData blockData) {
		if (blockData.getHeight() != 1)
			return false;

		byte[] signature = calcSignature(blockData);

		// Validate block signature
		if (!Arrays.equals(signature, blockData.getGeneratorSignature()))
			return false;

		// Validate transactions signature
		if (!Arrays.equals(signature, blockData.getTransactionsSignature()))
			return false;

		return true;
	}

	// Processing

	@Override
	public boolean addTransaction(TransactionData transactionData) {
		// The genesis block has a fixed set of transactions so always refuse.
		return false;
	}

	/**
	 * Refuse to calculate genesis block's generator signature!
	 * <p>
	 * This is not possible as there is no private key for the genesis account and so no way to sign data.
	 * <p>
	 * <b>Always throws IllegalStateException.</b>
	 * 
	 * @throws IllegalStateException
	 */
	@Override
	public void calcGeneratorSignature() {
		throw new IllegalStateException("There is no private key for genesis account");
	}

	/**
	 * Refuse to calculate genesis block's transactions signature!
	 * <p>
	 * This is not possible as there is no private key for the genesis account and so no way to sign data.
	 * <p>
	 * <b>Always throws IllegalStateException.</b>
	 * 
	 * @throws IllegalStateException
	 */
	@Override
	public void calcTransactionsSignature() {
		throw new IllegalStateException("There is no private key for genesis account");
	}

	/**
	 * Generate genesis block generator/transactions signature.
	 * <p>
	 * This is handled differently as there is no private key for the genesis account and so no way to sign data.
	 * <p>
	 * Instead we return the SHA-256 digest of the block, duplicated so that the returned byte[] is the same length as normal block signatures.
	 * 
	 * @return byte[]
	 */
	private static byte[] calcSignature(byte[] bytes) {
		byte[] digest = Crypto.digest(bytes);
		return Bytes.concat(digest, digest);
	}

	private static byte[] getBytesForSignature(int version, byte[] reference, BigDecimal generatingBalance, byte[] generatorPublicKey) {
		try {
			// Passing expected size to ByteArrayOutputStream avoids reallocation when adding more bytes than default 32.
			// See below for explanation of some of the values used to calculated expected size.
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + 64 + 8 + 32);

			/*
			 * NOTE: Historic code had genesis block using Longs.toByteArray() compared to standard block's Ints.toByteArray. The subsequent
			 * Bytes.ensureCapacity(versionBytes, 0, 4) did not truncate versionBytes back to 4 bytes either. This means 8 bytes were used even though
			 * VERSION_LENGTH is set to 4. Correcting this historic bug will break genesis block signatures!
			 */
			bytes.write(Longs.toByteArray(version));

			/*
			 * NOTE: Historic code had the reference expanded to only 64 bytes whereas standard block references are 128 bytes. Correcting this historic bug
			 * will break genesis block signatures!
			 */
			bytes.write(Bytes.ensureCapacity(reference, 64, 0));

			bytes.write(Longs.toByteArray(generatingBalance.longValue()));

			// NOTE: Genesis account's public key is only 8 bytes, not the usual 32, so we have to pad.
			bytes.write(Bytes.ensureCapacity(generatorPublicKey, 32, 0));

			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** Convenience method for calculating genesis block signatures from block data */
	private static byte[] calcSignature(BlockData blockData) {
		byte[] bytes = getBytesForSignature(blockData.getVersion(), blockData.getReference(), blockData.getGeneratingBalance(),
				blockData.getGeneratorPublicKey());
		return calcSignature(bytes);
	}

	@Override
	public boolean isSignatureValid() {
		byte[] signature = calcSignature(this.getBlockData());

		// Validate block signature
		if (!Arrays.equals(signature, this.getBlockData().getGeneratorSignature()))
			return false;

		// Validate transactions signature
		if (!Arrays.equals(signature, this.getBlockData().getTransactionsSignature()))
			return false;

		return true;
	}

	@Override
	public ValidationResult isValid() throws DataException {
		// Check there is no other block in DB
		if (this.repository.getBlockRepository().getBlockchainHeight() != 0)
			return ValidationResult.BLOCKCHAIN_NOT_EMPTY;

		// Validate transactions
		for (Transaction transaction : this.getTransactions())
			if (transaction.isValid() != Transaction.ValidationResult.OK)
				return ValidationResult.TRANSACTION_INVALID;

		return ValidationResult.OK;
	}

}
