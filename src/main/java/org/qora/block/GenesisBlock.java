package org.qora.block;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.account.Account;
import org.qora.account.GenesisAccount;
import org.qora.account.PublicKeyAccount;
import org.qora.crypto.Crypto;
import org.qora.data.asset.AssetData;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.TransactionTransformer;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

public class GenesisBlock extends Block {

	private static final Logger LOGGER = LogManager.getLogger(GenesisBlock.class);

	private static final byte[] GENESIS_REFERENCE = new byte[] {
		1, 1, 1, 1, 1, 1, 1, 1
	}; // NOTE: Neither 64 nor 128 bytes!

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class GenesisInfo {
		public int version = 1;
		public long timestamp;
		public BigDecimal generatingBalance;

		public TransactionData[] transactions;

		public GenesisInfo() {
		}
	}

	// Properties
	private static BlockData blockData;
	private static List<TransactionData> transactionsData;
	private static List<AssetData> initialAssets;

	// Constructors

	private GenesisBlock(Repository repository, BlockData blockData, List<TransactionData> transactions) throws DataException {
		super(repository, blockData, transactions, Collections.emptyList());
	}

	public static GenesisBlock getInstance(Repository repository) throws DataException {
		return new GenesisBlock(repository, blockData, transactionsData);
	}

	// Construction from JSON

	/** Construct block data from blockchain config */
	public static void newInstance(GenesisInfo info) {
		// Should be safe to make this call as BlockChain's instance is set
		// so we won't be blocked trying to re-enter synchronzied Settings.getInstance()
		BlockChain blockchain = BlockChain.getInstance();

		// Timestamp of zero means "now" but only valid for test nets!
		if (info.timestamp == 0) {
			if (!blockchain.isTestNet()) {
				LOGGER.error("Genesis timestamp of zero (i.e. now) not valid for non-testnet blockchain configs");
				throw new RuntimeException("Genesis timestamp of zero (i.e. now) not valid for non-testnet blockchain configs");
			}

			// This will only take effect if there is no current genesis block in blockchain
			info.timestamp = System.currentTimeMillis();
		}

		transactionsData = new ArrayList<TransactionData>(Arrays.asList(info.transactions));

		// Add default values to transactions
		transactionsData.stream().forEach(transactionData -> {
			if (transactionData.getFee() == null)
				transactionData.setFee(BigDecimal.ZERO.setScale(8));

			if (transactionData.getCreatorPublicKey() == null)
				transactionData.setCreatorPublicKey(GenesisAccount.PUBLIC_KEY);

			if (transactionData.getTimestamp() == 0)
				transactionData.setTimestamp(info.timestamp);
		});

		// For version 1, extract any ISSUE_ASSET transactions into initialAssets and only allow GENESIS transactions
		if (info.version == 1) {
			List<TransactionData> issueAssetTransactions = transactionsData.stream()
					.filter(transactionData -> transactionData.getType() == TransactionType.ISSUE_ASSET).collect(Collectors.toList());
			transactionsData.removeAll(issueAssetTransactions);

			// There should be only GENESIS transactions left;
			if (transactionsData.stream().anyMatch(transactionData -> transactionData.getType() != TransactionType.GENESIS)) {
				LOGGER.error("Version 1 genesis block only allowed to contain GENESIS transctions (after issue-asset processing)");
				throw new RuntimeException("Version 1 genesis block only allowed to contain GENESIS transctions (after issue-asset processing)");
			}

			// Convert ISSUE_ASSET transactions into initial assets
			initialAssets = issueAssetTransactions.stream().map(transactionData -> {
				IssueAssetTransactionData issueAssetTransactionData = (IssueAssetTransactionData) transactionData;

				return new AssetData(issueAssetTransactionData.getOwner(), issueAssetTransactionData.getAssetName(), issueAssetTransactionData.getDescription(),
						issueAssetTransactionData.getQuantity(), issueAssetTransactionData.getIsDivisible(), null, Group.NO_GROUP, issueAssetTransactionData.getReference());
			}).collect(Collectors.toList());
		}

		// Minor fix-up
		info.generatingBalance.setScale(8);

		byte[] reference = GENESIS_REFERENCE;
		int transactionCount = transactionsData.size();
		BigDecimal totalFees = BigDecimal.ZERO.setScale(8);
		byte[] generatorPublicKey = GenesisAccount.PUBLIC_KEY;
		byte[] bytesForSignature = getBytesForSignature(info.version, reference, info.generatingBalance, generatorPublicKey);
		byte[] generatorSignature = calcSignature(bytesForSignature);
		byte[] transactionsSignature = generatorSignature;
		int height = 1;
		int atCount = 0;
		BigDecimal atFees = BigDecimal.ZERO.setScale(8);

		blockData = new BlockData(info.version, reference, transactionCount, totalFees, transactionsSignature, height, info.timestamp, info.generatingBalance,
				generatorPublicKey, generatorSignature, atCount, atFees);
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

	public List<AssetData> getInitialAssets() {
		return Collections.unmodifiableList(initialAssets);
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

	@Override
	public void process() throws DataException {
		LOGGER.info(String.format("Using genesis block timestamp of %d", blockData.getTimestamp()));

		// If we're a version 1 genesis block, create assets now
		if (blockData.getVersion() == 1)
			for (AssetData assetData : initialAssets)
				repository.getAssetRepository().save(assetData);

		/*
		 * Some transactions will be missing references and signatures,
		 * so we generate them by trial-processing transactions and using
		 * account's last-reference to fill in the gaps for reference,
		 * and a duplicated SHA256 digest for signature
		 */
		this.repository.setSavepoint();
		try {
			for (Transaction transaction : this.getTransactions()) {
				TransactionData transactionData = transaction.getTransactionData();
				Account creator = new PublicKeyAccount(this.repository, transactionData.getCreatorPublicKey());

				// Missing reference?
				if (transactionData.getReference() == null)
					transactionData.setReference(creator.getLastReference());

				// Missing signature?
				if (transactionData.getSignature() == null) {
					byte[] digest = Crypto.digest(TransactionTransformer.toBytesForSigning(transactionData));
					byte[] signature = Bytes.concat(digest, digest);

					transactionData.setSignature(signature);
				}

				// Missing approval status (not used in V1)
				transactionData.setApprovalStatus(ApprovalStatus.NOT_REQUIRED);

				// Ask transaction to update references, etc.
				transaction.processReferencesAndFees();

				creator.setLastReference(transactionData.getSignature());
			}
		} catch (TransformationException e) {
			throw new RuntimeException("Can't process genesis block transaction", e);
		} finally {
			this.repository.rollbackToSavepoint();
		}

		// Save transactions into repository ready for processing
		for (Transaction transaction : this.getTransactions())
			this.repository.getTransactionRepository().save(transaction.getTransactionData());

		super.process();
	}

}
