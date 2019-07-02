package org.qora.block;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.Base58;
import org.eclipse.persistence.exceptions.XMLMarshalException;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.qora.controller.Controller;
import org.qora.data.account.ProxyForgerData;
import org.qora.data.block.BlockData;
import org.qora.data.block.BlockSummaryData;
import org.qora.network.Network;
import org.qora.repository.BlockRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.utils.ByteArray;
import org.qora.utils.StringLongMapXmlAdapter;

/**
 * Class representing the blockchain as a whole.
 *
 */
// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class BlockChain {

	private static final Logger LOGGER = LogManager.getLogger(BlockChain.class);

	public static final byte[] CANCEL_ASSET_ORDER_BLOCK_SIG = Base58.decode("DCYjHWLN3S4Ta7EVeGdxoTfAS9sBdp7Ldr2B6cuyXFqZecQbJT6WUP68kd2Xz31REXWPTvmXngWrMb7bFyMpMhkAtokfDr8vWXbmbPXeoTQi7EGfgpGrhVn45zejvG8iJWbKd7c3z8GGFz7yPYL4cU4HnyD6jqJDrAW6XqBi4nW2dWE");
	public static final byte[] CANCEL_ASSET_ORDER_TX_SIG = Base58.decode("26e21JyKTHcteozWK8sVstSk11fMq2UtULddTg6cBvTwTVRhdLfETsshwShVpDL5dPrzxQuB1xgh72kdQx6VdZyR");

	private static BlockChain instance = null;

	// Properties

	private boolean isTestChain = false;
	/** Maximum coin supply. */
	private BigDecimal maxBalance;

	private BigDecimal unitFee;
	private BigDecimal maxBytesPerUnitFee;
	private BigDecimal minFeePerByte;

	/** Number of blocks between recalculating block's generating balance. */
	private int blockDifficultyInterval;
	/** Maximum acceptable timestamp disagreement offset in milliseconds. */
	private long blockTimestampMargin;

	/** Whether transactions with txGroupId of NO_GROUP are allowed */
	private boolean requireGroupForApproval;

	private GenesisBlock.GenesisInfo genesisInfo;

	public enum FeatureTrigger {
		messageHeight,
		atHeight,
		newBlockDistanceHeight,
		newBlockTimingHeight,
		assetsTimestamp,
		votingTimestamp,
		arbitraryTimestamp,
		powfixTimestamp,
		v2Timestamp,
		newAssetPricingTimestamp,
		groupApprovalTimestamp;
	}

	/** Map of which blockchain features are enabled when (height/timestamp) */
	@XmlJavaTypeAdapter(StringLongMapXmlAdapter.class)
	private Map<String, Long> featureTriggers;

	/** Whether to use legacy, broken RIPEMD160 implementation when converting public keys to addresses. */
	private boolean useBrokenMD160ForAddresses = false;

	/** Whether only one registered name is allowed per account. */
	private boolean oneNamePerAccount = false;

	/** Block rewards by block height */
	public static class RewardByHeight {
		public int height;
		public BigDecimal reward;
	}
	List<RewardByHeight> rewardsByHeight;

	/** Block times by block height */
	public static class BlockTimingByHeight {
		public int height;
		public long target; // ms
		public long deviation; // ms
		public double power;
	}
	List<BlockTimingByHeight> blockTimingsByHeight;

	/** Forging right tiers */
	public static class ForgingTier {
		/** Minimum number of blocks forged before account can enable minting on other accounts. */
		public int minBlocks;
		/** Maximum number of other accounts that can be enabled. */
		public int maxSubAccounts;
	}
	List<ForgingTier> forgingTiers;

	private int maxProxyRelationships;

	// Constructors, etc.

	private BlockChain() {
	}

	public static BlockChain getInstance() {
		if (instance == null)
			// This will call BlockChain.fromJSON in turn
			Settings.getInstance(); // synchronized

		return instance;
	}

	/** Use blockchain config read from <tt>path</tt> + <tt>filename</tt>, or use resources-based default if <tt>filename</tt> is <tt>null</tt>. */
	public static void fileInstance(String path, String filename) {
		JAXBContext jc;
		Unmarshaller unmarshaller;

		try {
			// Create JAXB context aware of Settings
			jc = JAXBContextFactory.createContext(new Class[] {
				BlockChain.class, GenesisBlock.GenesisInfo.class
			}, null);

			// Create unmarshaller
			unmarshaller = jc.createUnmarshaller();

			// Set the unmarshaller media type to JSON
			unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");

			// Tell unmarshaller that there's no JSON root element in the JSON input
			unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);

		} catch (JAXBException e) {
			LOGGER.error("Unable to process blockchain config file", e);
			throw new RuntimeException("Unable to process blockchain config file", e);
		}

		BlockChain blockchain = null;
		StreamSource jsonSource;

		if (filename != null) {
			LOGGER.info("Using blockchain config file: " + path + filename);

			File jsonFile = new File(path + filename);

			if (!jsonFile.exists()) {
				LOGGER.error("Blockchain config file not found: " + path + filename);
				throw new RuntimeException("Blockchain config file not found: " + path + filename);
			}

			jsonSource = new StreamSource(jsonFile);
		} else {
			LOGGER.info("Using default, resources-based blockchain config");

			ClassLoader classLoader = BlockChain.class.getClassLoader();
			InputStream in = classLoader.getResourceAsStream("blockchain.json");
			jsonSource = new StreamSource(in);
		}

		try  {
			// Attempt to unmarshal JSON stream to BlockChain config
			blockchain = unmarshaller.unmarshal(jsonSource, BlockChain.class).getValue();
		} catch (UnmarshalException e) {
			Throwable linkedException = e.getLinkedException();
			if (linkedException instanceof XMLMarshalException) {
				String message = ((XMLMarshalException) linkedException).getInternalException().getLocalizedMessage();
				LOGGER.error(message);
				throw new RuntimeException(message);
			}

			LOGGER.error("Unable to process blockchain config file", e);
			throw new RuntimeException("Unable to process blockchain config file", e);
		} catch (JAXBException e) {
			LOGGER.error("Unable to process blockchain config file", e);
			throw new RuntimeException("Unable to process blockchain config file", e);
		}

		// Validate config
		blockchain.validateConfig();

		// Minor fix-up
		blockchain.maxBytesPerUnitFee.setScale(8);
		blockchain.unitFee.setScale(8);
		blockchain.minFeePerByte = blockchain.unitFee.divide(blockchain.maxBytesPerUnitFee, MathContext.DECIMAL32);

		// Successfully read config now in effect
		instance = blockchain;

		// Pass genesis info to GenesisBlock
		GenesisBlock.newInstance(blockchain.genesisInfo);
	}

	// Getters / setters

	public boolean isTestChain() {
		return this.isTestChain;
	}

	public BigDecimal getUnitFee() {
		return this.unitFee;
	}

	public BigDecimal getMaxBytesPerUnitFee() {
		return this.maxBytesPerUnitFee;
	}

	public BigDecimal getMinFeePerByte() {
		return this.minFeePerByte;
	}

	public BigDecimal getMaxBalance() {
		return this.maxBalance;
	}

	public int getBlockDifficultyInterval() {
		return this.blockDifficultyInterval;
	}

	public long getBlockTimestampMargin() {
		return this.blockTimestampMargin;
	}

	/** Returns true if approval-needing transaction types require a txGroupId other than NO_GROUP. */
	public boolean getRequireGroupForApproval() {
		return this.requireGroupForApproval;
	}

	public boolean getUseBrokenMD160ForAddresses() {
		return this.useBrokenMD160ForAddresses;
	}

	public boolean oneNamePerAccount() {
		return this.oneNamePerAccount;
	}

	public List<RewardByHeight> getBlockRewardsByHeight() {
		return this.rewardsByHeight;
	}

	public List<ForgingTier> getForgingTiers() {
		return this.forgingTiers;
	}

	public int getMaxProxyRelationships() {
		return this.maxProxyRelationships;
	}

	// Convenience methods for specific blockchain feature triggers

	public long getMessageReleaseHeight() {
		return featureTriggers.get("messageHeight");
	}

	public long getATReleaseHeight() {
		return featureTriggers.get("atHeight");
	}

	public int getNewBlockDistanceHeight() {
		return featureTriggers.get("newBlockDistanceHeight").intValue();
	}

	public int getNewBlockTimingHeight() {
		return featureTriggers.get("newBlockTimingHeight").intValue();
	}

	public long getPowFixReleaseTimestamp() {
		return featureTriggers.get("powfixTimestamp");
	}

	public long getAssetsReleaseTimestamp() {
		return featureTriggers.get("assetsTimestamp");
	}

	public long getVotingReleaseTimestamp() {
		return featureTriggers.get("votingTimestamp");
	}

	public long getArbitraryReleaseTimestamp() {
		return featureTriggers.get("arbitraryTimestamp");
	}

	public long getQoraV2Timestamp() {
		return featureTriggers.get("v2Timestamp");
	}

	public long getNewAssetPricingTimestamp() {
		return featureTriggers.get("newAssetPricingTimestamp");
	}

	public long getGroupApprovalTimestamp() {
		return featureTriggers.get("groupApprovalTimestamp");
	}

	// More complex getters for aspects that change by height or timestamp

	public BigDecimal getRewardAtHeight(int ourHeight) {
		// Scan through for reward at our height
		for (int i = rewardsByHeight.size() - 1; i >= 0; --i)
			if (rewardsByHeight.get(i).height <= ourHeight)
				return rewardsByHeight.get(i).reward;

		return null;
	}

	public BlockTimingByHeight getBlockTimingByHeight(int ourHeight) {
		for (int i = blockTimingsByHeight.size() - 1; i >= 0; --i)
			if (blockTimingsByHeight.get(i).height <= ourHeight)
				return blockTimingsByHeight.get(i);

		throw new IllegalStateException(String.format("No block timing info available for height %d", ourHeight));
	}

	/** Validate blockchain config read from JSON */
	private void validateConfig() {
		if (this.genesisInfo == null) {
			LOGGER.error("No \"genesisInfo\" entry found in blockchain config");
			throw new RuntimeException("No \"genesisInfo\" entry found in blockchain config");
		}

		if (this.featureTriggers == null) {
			LOGGER.error("No \"featureTriggers\" entry found in blockchain config");
			throw new RuntimeException("No \"featureTriggers\" entry found in blockchain config");
		}

		// Check all featureTriggers are present
		for (FeatureTrigger featureTrigger : FeatureTrigger.values())
			if (!this.featureTriggers.containsKey(featureTrigger.name())) {
				LOGGER.error(String.format("Missing feature trigger \"%s\" in blockchain config", featureTrigger.name()));
				throw new RuntimeException("Missing feature trigger in blockchain config");
			}

		if (this.blockTimingsByHeight == null || this.blockTimingsByHeight.isEmpty()) {
			LOGGER.error("No \"blockTimesByHeight\" entry found in blockchain config");
			throw new RuntimeException("No \"blockTimesByHeight\" entry found in blockchain config");
		}
	}

	/**
	 * Some sort start-up/initialization/checking method.
	 * 
	 * @throws SQLException
	 */
	public static void validate() throws DataException {
		// Check first block is Genesis Block
		if (!isGenesisBlockValid())
			rebuildBlockchain();

		// Walk through blocks
		try (final Repository repository = RepositoryManager.getRepository()) {
			Block parentBlock = GenesisBlock.getInstance(repository);
			BlockData parentBlockData = parentBlock.getBlockData();

			while (true) {
				BlockData childBlockData = parentBlock.getChild();
				if (childBlockData == null)
					break;

				if (!Arrays.equals(childBlockData.getReference(), parentBlock.getSignature())) {
					LOGGER.error(String.format("Block %d's reference does not match block %d's signature", childBlockData.getHeight(), parentBlockData.getHeight()));
					rebuildBlockchain();
					return;
				}

				parentBlock = new Block(repository, childBlockData);
				parentBlockData = childBlockData;
			}
		}

		// Potential repairs
		repairCancelAssetOrderBugfix();
	}

	private static void repairCancelAssetOrderBugfix() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			final boolean hasPreRollbackBlock = repository.getBlockRepository().getHeightFromSignature(CANCEL_ASSET_ORDER_BLOCK_SIG) != 0;
			final boolean hasInvalidTransaction = repository.getTransactionRepository().isConfirmed(CANCEL_ASSET_ORDER_TX_SIG);

			if (!hasPreRollbackBlock && !hasInvalidTransaction)
				return;
		}

		TimerTask rollbackTask = new TimerTask() {
			public void run() {
				final int targetBlockHeight = 5387;

				LOGGER.info(String.format("Preparing to rollback for CANCEL_ASSET_ORDER bugfix"));

				ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
				blockchainLock.lock();

				try (final Repository repository = RepositoryManager.getRepository()) {
					LOGGER.info(String.format("Rolling back to block %d for CANCEL_ASSET_ORDER bugfix", targetBlockHeight));

					for (int height = repository.getBlockRepository().getBlockchainHeight(); height > targetBlockHeight; --height) {
						BlockData blockData = repository.getBlockRepository().fromHeight(height);
						Block block = new Block(repository, blockData);
						block.orphan();
						repository.saveChanges();
					}
				} catch (DataException e) {
					LOGGER.warn(String.format("Rolled for CANCEL_ASSET_ORDER bugfix failed - will retry soon"));
				} finally {
					blockchainLock.unlock();
				}

				LOGGER.info(String.format("Rolled back to block %d for CANCEL_ASSET_ORDER bugfix", targetBlockHeight));
				cancel();
			}
		};

		// Set up time-based trigger for rollback
		final long triggerTimestamp = 1560862800_000L; // Tue Jun 18 13:00:00.000 2019 UTC+0000

		// How long to wait? (Minimum 0 seconds)
		long delay = Math.max(0, triggerTimestamp - System.currentTimeMillis());
		LOGGER.info(String.format("Scheduling rollback for CANCEL_ASSET_ORDER bugfix in %d seconds", delay / 1000));

		// If rollback failed - try again after 5 minutes
		final long interval = 5 * 60 * 1000;

		Timer timer = new Timer("RollbackTimer");
		timer.schedule(rollbackTask, delay, interval);
	}

	private static boolean isGenesisBlockValid() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockRepository blockRepository = repository.getBlockRepository();

			int blockchainHeight = blockRepository.getBlockchainHeight();
			if (blockchainHeight < 1)
				return false;

			BlockData blockData = blockRepository.fromHeight(1);
			if (blockData == null)
				return false;

			return GenesisBlock.isGenesisBlock(blockData);
		}
	}

	private static void rebuildBlockchain() throws DataException {
		// (Re)build repository
		try (final Repository repository = RepositoryManager.getRepository()) {
			repository.rebuild();

			GenesisBlock genesisBlock = GenesisBlock.getInstance(repository);

			// Add Genesis Block to blockchain
			genesisBlock.process();

			repository.saveChanges();

			// Give Network a change to install initial seed peers
			Network.installInitialPeers(repository);
		}
	}

	/**
	 * Returns distances from 'ideal' for each of passed blockchains.
	 * <p>
	 * 'Ideal' block is based on previous block data.<br>
	 * Passed blockchains all follow on from passed parent block.
	 * <p>
	 * Passed blocks are first compared using 'root' forger's public key.
	 * <p>
	 * If more than one block shares the smallest distance, then a second round
	 * uses the proxy public key instead. After the second round, losing blocks from the
	 * first round have their distance set to be worse than the worst block from 2nd round.
	 * <p>
	 * 'Root' forger means the account that either forged the block directly, or
	 * is the account with minting right enable in a proxy-forging relationship, i.e. not
	 * the reward-share recipient.
	 * 
	 * @param repository
	 * @param parentBlockSummary
	 * @param comparableBlockSummaries
	 * @return
	 * @throws DataException
	 */
	public static List<BigInteger> calcBlockchainDistances(Repository repository, BlockSummaryData parentBlockSummary, List<List<BlockSummaryData>> allBlockSummaries) throws DataException {
		final int nChains = allBlockSummaries.size();

		List<HashSet<ByteArray>> uniqueForgersPerChain = new ArrayList<>(nChains);
		int largestChainSize = 0;
		for (int ci = 0; ci < nChains; ++ci) {
			uniqueForgersPerChain.add(new HashSet<ByteArray>());
			largestChainSize = Math.max(largestChainSize, allBlockSummaries.get(ci).size());
		}

		final int nBlocks = largestChainSize;

		HashMap<ByteArray, ProxyForgerData> cachedProxyForgerData = new HashMap<>();

		List<BigInteger> totalDistances = new ArrayList<>(Collections.nCopies(nChains, BigInteger.ZERO));

		List<BlockSummaryData> parentSummaries = new ArrayList<>(Collections.nCopies(nChains, parentBlockSummary));

		int height = parentBlockSummary.getHeight();
		for (int bi = 0; bi < nBlocks; ++bi) {
			++height;

			final boolean isNewBehaviour = height >= BlockChain.getInstance().getNewBlockDistanceHeight();
			List<BigInteger> distances = new ArrayList<>(nChains);
			List<BigInteger> idealGenerators = new ArrayList<>(nChains);

			int indexOfClosest = 0;

			// 1st round - compare root forgers
			for (int ci = 0; ci < nChains; ++ci) {
				// Per chain

				// 'Ideal'
				byte[] idealGenerator = Block.calcIdealGeneratorPublicKey(height - 1, parentSummaries.get(ci).getSignature());
				BigInteger idealGeneratorBI = new BigInteger(idealGenerator);
				idealGenerators.add(idealGeneratorBI);

				BlockSummaryData blockSummaryData = getPaddedBlockSummary(allBlockSummaries, ci, bi);
				ByteArray generatorBA = new ByteArray(blockSummaryData.getGeneratorPublicKey());

				// Check for proxy forging
				ProxyForgerData proxyForgerData = cachedProxyForgerData.get(generatorBA);
				if (proxyForgerData == null && !cachedProxyForgerData.containsKey(generatorBA)) {
					proxyForgerData = repository.getAccountRepository().getProxyForgeData(generatorBA.raw);
					cachedProxyForgerData.put(generatorBA, proxyForgerData);
				}

				byte[] publicKey;
				if (isNewBehaviour)
					// New behaviour: If proxy forged then use forger's key
					publicKey = proxyForgerData != null ? proxyForgerData.getForgerPublicKey() : blockSummaryData.getGeneratorPublicKey();
				else
					// Previous behaviour only ever uses block's "generator" public key, which is typically proxy public key
					publicKey = blockSummaryData.getGeneratorPublicKey();

				// Unique forger?
				uniqueForgersPerChain.get(ci).add(new ByteArray(publicKey));

				byte[] perturbedPublicKey = Block.calcHeightPerturbedPublicKey(height, publicKey);
				BigInteger generatorBI = new BigInteger(perturbedPublicKey);

				BigInteger distance = idealGeneratorBI.subtract(generatorBI).abs();
				distances.add(distance);

				// Is this the closest to ideal (i.e. smallest distance)?
				if (distance.compareTo(distances.get(indexOfClosest)) < 0)
					indexOfClosest = ci;

				// Update parent summary
				parentSummaries.set(ci, blockSummaryData);
			}

			BigInteger smallestDistance = distances.get(indexOfClosest);

			// If there are more than one block summaries with the same root forging account then we need to do round two
			// (This should not happen for blocks under old behaviour)
			final long nSmallest = distances.stream().filter(distance -> distance.compareTo(smallestDistance) == 0).count();
			if (nSmallest > 1) {
				// 2nd round - compare proxy-forged blocks with same root forger

				BlockSummaryData smallestBlockSummaryData = getPaddedBlockSummary(allBlockSummaries, indexOfClosest, bi);

				// Forger from block(s) with smallest distance from 1st round
				ProxyForgerData smallestProxyForgerData = cachedProxyForgerData.get(new ByteArray(smallestBlockSummaryData.getGeneratorPublicKey()));
				if (smallestProxyForgerData == null) {
					// Wasn't proxy forged - multiple chains with a block forged directly
				} else {
					byte[] forgerPublicKey = smallestProxyForgerData.getForgerPublicKey();

					// Keep track of largest distance
					Integer indexOfLargest = null;

					for (int ci = 0; ci < nChains; ++ci) {
						BlockSummaryData blockSummaryData = getPaddedBlockSummary(allBlockSummaries, ci, bi);
						ProxyForgerData proxyForgerData = cachedProxyForgerData.get(new ByteArray(blockSummaryData.getGeneratorPublicKey()));

						// We're only interested in blocks with the same root forger as the one with smallest distance from 1st round
						if (proxyForgerData == null || !Arrays.equals(proxyForgerData.getForgerPublicKey(), forgerPublicKey))
							continue;

						// Compare using proxy public key
						byte[] perturbedPublicKey = Block.calcHeightPerturbedPublicKey(blockSummaryData.getHeight(), proxyForgerData.getProxyPublicKey());
						BigInteger generatorBI = new BigInteger(perturbedPublicKey);

						BigInteger distance = idealGenerators.get(ci).subtract(generatorBI).abs();
						distances.set(ci, distance);

						// Is this the largest distance?
						if (indexOfLargest == null || distance.compareTo(distances.get(indexOfLargest)) > 0)
							indexOfLargest = ci;
					}

					// Set distances of all other blocks NOT with same root forger to largest distance + 1
					// This is so those blocks appear 'worse' than all the ones processed in 2nd round
					BigInteger fakeLargestDistance = distances.get(indexOfLargest).add(BigInteger.ONE);
					for (int ci = 0; ci < nChains; ++ci) {
						BlockSummaryData blockSummaryData = getPaddedBlockSummary(allBlockSummaries, ci, bi);
						ProxyForgerData proxyForgerData = cachedProxyForgerData.get(new ByteArray(blockSummaryData.getGeneratorPublicKey()));

						// We're only interested in blocks WITHOUT the same root forger as the one with smallest distance from 1st round
						if (proxyForgerData !=null && Arrays.equals(proxyForgerData.getForgerPublicKey(), forgerPublicKey))
							continue;

						distances.set(ci, fakeLargestDistance);
					}
				}
			}

			// Add final distance to total for each blockchain
			for (int ci = 0; ci < nChains; ++ci)
				totalDistances.set(ci, totalDistances.get(ci).add(distances.get(ci)));
		}

		// A variety of generators is a benefit
		for (int ci = 0; ci < nChains; ++ci) {
			BigInteger uniqueForgers = BigInteger.valueOf(uniqueForgersPerChain.get(ci).size());
			totalDistances.set(ci, totalDistances.get(ci).divide(uniqueForgers));
		}

		return totalDistances;
	}

	private static BlockSummaryData getPaddedBlockSummary(List<List<BlockSummaryData>> allBlockSummaries, int chainIndex, int blockIndex) {
		List<BlockSummaryData> blockSummaries = allBlockSummaries.get(chainIndex);

		final int size = blockSummaries.size();

		if (blockIndex >= size)
			blockIndex = size - 1;

		return blockSummaries.get(blockIndex);
	}

	public static boolean orphan(int targetHeight) throws DataException {
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		if (!blockchainLock.tryLock())
			return false;

		try {
			try (final Repository repository = RepositoryManager.getRepository()) {
				for (int height = repository.getBlockRepository().getBlockchainHeight(); height > targetHeight; --height) {
					LOGGER.info(String.format("Forcably orphaning block %d", height));

					BlockData blockData = repository.getBlockRepository().fromHeight(height);
					Block block = new Block(repository, blockData);
					block.orphan();
					repository.saveChanges();
				}

				return true;
			}
		} finally {
			blockchainLock.unlock();
		}
	}

}
