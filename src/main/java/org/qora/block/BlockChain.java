package org.qora.block;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.eclipse.persistence.exceptions.XMLMarshalException;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.qora.crypto.Crypto;
import org.qora.data.block.BlockData;
import org.qora.data.network.BlockSummaryData;
import org.qora.network.Network;
import org.qora.repository.BlockRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;
import org.qora.utils.StringLongMapXmlAdapter;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

/**
 * Class representing the blockchain as a whole.
 *
 */
// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class BlockChain {

	private static final Logger LOGGER = LogManager.getLogger(BlockChain.class);

	private static BlockChain instance = null;

	// Properties

	private boolean isTestNet = false;
	/** Maximum coin supply. */
	private BigDecimal maxBalance;

	private BigDecimal unitFee;
	private BigDecimal maxBytesPerUnitFee;
	private BigDecimal minFeePerByte;

	/** Number of blocks between recalculating block's generating balance. */
	private int blockDifficultyInterval;
	/** Minimum target time between blocks, in seconds. */
	private long minBlockTime;
	/** Maximum target time between blocks, in seconds. */
	private long maxBlockTime;
	/** Maximum acceptable timestamp disagreement offset in milliseconds. */
	private long blockTimestampMargin;

	/** Whether transactions with txGroupId of NO_GROUP are allowed */
	private boolean requireGroupForApproval;

	private GenesisBlock.GenesisInfo genesisInfo;

	public enum FeatureTrigger {
		messageHeight,
		atHeight,
		assetsTimestamp,
		votingTimestamp,
		arbitraryTimestamp,
		powfixTimestamp,
		v2Timestamp,
		newAssetPricingTimestamp;
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

	/** Forging right tiers */
	public static class ForgingTier {
		/** Minimum number of blocks forged before account can enable minting on other accounts. */
		public int minBlocks;
		/** Maximum number of other accounts that can be enabled. */
		public int maxSubAccounts;
	}
	List<ForgingTier> forgingTiers;

	// Constructors, etc.

	private BlockChain() {
	}

	public static BlockChain getInstance() {
		if (instance == null)
			// This will call BlockChain.fromJSON in turn
			Settings.getInstance(); // synchronized

		return instance;
	}

	public static void fileInstance(String filename) {
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

		LOGGER.info("Using blockchain config file: " + filename);

		// Create the StreamSource by creating Reader to the JSON input
		try (Reader settingsReader = new FileReader(filename)) {
			StreamSource json = new StreamSource(settingsReader);

			// Attempt to unmarshal JSON stream to BlockChain config
			blockchain = unmarshaller.unmarshal(json, BlockChain.class).getValue();
		} catch (UnmarshalException e) {
			Throwable linkedException = e.getLinkedException();
			if (linkedException instanceof XMLMarshalException) {
				String message = ((XMLMarshalException) linkedException).getInternalException().getLocalizedMessage();
				LOGGER.error(message);
				throw new RuntimeException(message);
			}

			LOGGER.error("Unable to process blockchain config file", e);
			throw new RuntimeException("Unable to process blockchain config file", e);
		} catch (FileNotFoundException e) {
			LOGGER.error("Blockchain config file not found: " + filename);
			throw new RuntimeException("Blockchain config file not found: " + filename);
		} catch (JAXBException e) {
			LOGGER.error("Unable to process blockchain config file", e);
			throw new RuntimeException("Unable to process blockchain config file", e);
		} catch (IOException e) {
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

	public boolean isTestNet() {
		return this.isTestNet;
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

	public long getMinBlockTime() {
		return this.minBlockTime;
	}

	public long getMaxBlockTime() {
		return this.maxBlockTime;
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

	// Convenience methods for specific blockchain feature triggers

	public long getMessageReleaseHeight() {
		return featureTriggers.get("messageHeight");
	}

	public long getATReleaseHeight() {
		return featureTriggers.get("atHeight");
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

	public static BigInteger calcBlockchainDistance(BlockSummaryData parentBlockSummary, List<BlockSummaryData> blockSummaries) {
		BigInteger weight = BigInteger.ZERO;

		HashSet<String> seenGenerators = new HashSet<>();

		for (BlockSummaryData blockSummary : blockSummaries) {
			byte[] idealGenerator = Block.calcIdealGeneratorPublicKey(parentBlockSummary.getHeight(), parentBlockSummary.getSignature());
			BigInteger idealBI = new BigInteger(idealGenerator);

			byte[] heightPerturbedGenerator = Crypto.digest(Bytes.concat(Longs.toByteArray(blockSummary.getHeight()), blockSummary.getGeneratorPublicKey()));
			BigInteger distance = new BigInteger(heightPerturbedGenerator).subtract(idealBI).abs();

			weight = weight.add(distance);

			seenGenerators.add(Crypto.toAddress(blockSummary.getGeneratorPublicKey()));

			parentBlockSummary = blockSummary;
		}

		// A variety of generators is a benefit
		weight = weight.divide(BigInteger.valueOf(seenGenerators.size()));

		return weight;
	}

	public static BigInteger calcBlockchainDistance(Repository repository, int firstBlockHeight, int lastBlockHeight) throws DataException {
		BlockData parentBlockData = repository.getBlockRepository().fromHeight(firstBlockHeight - 1);
		BlockSummaryData parentBlockSummary = new BlockSummaryData(parentBlockData);

		List<BlockData> blocksData = repository.getBlockRepository().getBlocks(firstBlockHeight, lastBlockHeight);
		List<BlockSummaryData> blockSummaries = blocksData.stream().map(blockData -> new BlockSummaryData(blockData)).collect(Collectors.toList());

		return BlockChain.calcBlockchainDistance(parentBlockSummary, blockSummaries);
	}

}
