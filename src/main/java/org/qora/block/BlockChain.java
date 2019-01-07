package org.qora.block;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.simple.JSONObject;
import org.qora.data.asset.AssetData;
import org.qora.data.block.BlockData;
import org.qora.repository.BlockRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.settings.Settings;

/**
 * Class representing the blockchain as a whole.
 *
 */
public class BlockChain {

	private static final Logger LOGGER = LogManager.getLogger(BlockChain.class);

	public enum FeatureValueType {
		height,
		timestamp;
	}

	private static BlockChain instance = null;

	// Properties
	private BigDecimal unitFee;
	private BigDecimal maxBytesPerUnitFee;
	private BigDecimal minFeePerByte;
	/** Maximum coin supply. */
	private BigDecimal maxBalance;;
	/** Number of blocks between recalculating block's generating balance. */
	private int blockDifficultyInterval;
	/** Minimum target time between blocks, in seconds. */
	private long minBlockTime;
	/** Maximum target time between blocks, in seconds. */
	private long maxBlockTime;
	/** Maximum acceptable timestamp disagreement offset in milliseconds. */
	private long blockTimestampMargin;
	/** Map of which blockchain features are enabled when (height/timestamp) */
	private Map<String, Map<FeatureValueType, Long>> featureTriggers;

	// This property is slightly different as we need it early and we want to avoid getInstance() loop
	private static boolean useBrokenMD160ForAddresses = false;

	// Constructors, etc.

	private BlockChain() {
	}

	public static BlockChain getInstance() {
		if (instance == null)
			// This will call BlockChain.fromJSON in turn
			Settings.getInstance(); // synchronized

		return instance;
	}

	// Getters / setters

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

	public static boolean getUseBrokenMD160ForAddresses() {
		return useBrokenMD160ForAddresses;
	}

	private long getFeatureTrigger(String feature, FeatureValueType valueType) {
		Map<FeatureValueType, Long> featureTrigger = featureTriggers.get(feature);
		if (featureTrigger == null)
			return 0;

		Long value = featureTrigger.get(valueType);
		if (value == null)
			return 0;

		return value;
	}

	// Convenience methods for specific blockchain feature triggers

	public long getMessageReleaseHeight() {
		return getFeatureTrigger("message", FeatureValueType.height);
	}

	public long getATReleaseHeight() {
		return getFeatureTrigger("AT", FeatureValueType.height);
	}

	public long getPowFixReleaseTimestamp() {
		return getFeatureTrigger("powfix", FeatureValueType.timestamp);
	}

	public long getAssetsReleaseTimestamp() {
		return getFeatureTrigger("assets", FeatureValueType.timestamp);
	}

	public long getVotingReleaseTimestamp() {
		return getFeatureTrigger("voting", FeatureValueType.timestamp);
	}

	public long getArbitraryReleaseTimestamp() {
		return getFeatureTrigger("arbitrary", FeatureValueType.timestamp);
	}

	public long getQoraV2Timestamp() {
		return getFeatureTrigger("v2", FeatureValueType.timestamp);
	}

	// Blockchain config from JSON

	public static void fromJSON(JSONObject json) {
		// Determine hash function for generating addresses as we need that to build genesis block, etc.
		Boolean useBrokenMD160 = null;
		if (json.containsKey("useBrokenMD160ForAddresses"))
			useBrokenMD160 = (Boolean) Settings.getTypedJson(json, "useBrokenMD160ForAddresses", Boolean.class);

		if (useBrokenMD160 != null)
			useBrokenMD160ForAddresses = useBrokenMD160.booleanValue();

		Object genesisJson = json.get("genesis");
		if (genesisJson == null) {
			LOGGER.error("No \"genesis\" entry found in blockchain config");
			throw new RuntimeException("No \"genesis\" entry found in blockchain config");
		}
		GenesisBlock.fromJSON((JSONObject) genesisJson);

		// Simple blockchain properties
		BigDecimal unitFee = Settings.getJsonBigDecimal(json, "unitFee");
		long maxBytesPerUnitFee = (Long) Settings.getTypedJson(json, "maxBytesPerUnitFee", Long.class);
		BigDecimal maxBalance = Settings.getJsonBigDecimal(json, "coinSupply");
		int blockDifficultyInterval = ((Long) Settings.getTypedJson(json, "blockDifficultyInterval", Long.class)).intValue();
		long minBlockTime = 1000L * (Long) Settings.getTypedJson(json, "minBlockTime", Long.class); // config entry in seconds
		long maxBlockTime = 1000L * (Long) Settings.getTypedJson(json, "maxBlockTime", Long.class); // config entry in seconds
		long blockTimestampMargin = (Long) Settings.getTypedJson(json, "blockTimestampMargin", Long.class); // config entry in milliseconds

		// blockchain feature triggers
		Map<String, Map<FeatureValueType, Long>> featureTriggers = new HashMap<>();
		JSONObject featuresJson = (JSONObject) Settings.getTypedJson(json, "featureTriggers", JSONObject.class);
		for (Object feature : featuresJson.keySet()) {
			String featureKey = (String) feature;
			JSONObject trigger = (JSONObject) Settings.getTypedJson(featuresJson, featureKey, JSONObject.class);

			if (!trigger.containsKey("height") && !trigger.containsKey("timestamp")) {
				LOGGER.error("Feature trigger \"" + featureKey + "\" must contain \"height\" or \"timestamp\" in blockchain config file");
				throw new RuntimeException("Feature trigger \"" + featureKey + "\" must contain \"height\" or \"timestamp\" in blockchain config file");
			}

			String triggerKey = (String) trigger.keySet().iterator().next();
			FeatureValueType featureValueType = FeatureValueType.valueOf(triggerKey);
			if (featureValueType == null) {
				LOGGER.error("Unrecognised feature trigger value type \"" + triggerKey + "\" for feature \"" + featureKey + "\" in blockchain config file");
				throw new RuntimeException(
						"Unrecognised feature trigger value type \"" + triggerKey + "\" for feature \"" + featureKey + "\" in blockchain config file");
			}

			Long value = (Long) Settings.getJsonQuotedLong(trigger, triggerKey);

			featureTriggers.put(featureKey, Collections.singletonMap(featureValueType, value));
		}

		instance = new BlockChain();
		instance.unitFee = unitFee;
		instance.maxBytesPerUnitFee = BigDecimal.valueOf(maxBytesPerUnitFee).setScale(8);
		instance.minFeePerByte = unitFee.divide(instance.maxBytesPerUnitFee, MathContext.DECIMAL32);
		instance.maxBalance = maxBalance;
		instance.blockDifficultyInterval = blockDifficultyInterval;
		instance.minBlockTime = minBlockTime;
		instance.maxBlockTime = maxBlockTime;
		instance.blockTimestampMargin = blockTimestampMargin;
		instance.featureTriggers = featureTriggers;
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

			// Add initial assets
			// NOTE: Asset's [transaction] reference doesn't exist as a transaction!
			for (AssetData assetData : genesisBlock.getInitialAssets())
				repository.getAssetRepository().save(assetData);

			// Add Genesis Block to blockchain
			genesisBlock.process();

			repository.saveChanges();
		}
	}

}
