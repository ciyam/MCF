package settings;

import qora.block.GenesisBlock;

public class Settings {

	private static Settings instance;

	// Properties
	private long genesisTimestamp = -1;

	public static Settings getInstance() {
		if (instance == null)
			instance = new Settings();

		return instance;
	}

	public int getMaxBytePerFee() {
		return 1024;
	}

	public long getGenesisTimestamp() {
		if (this.genesisTimestamp != -1)
			return this.genesisTimestamp;

		return GenesisBlock.GENESIS_TIMESTAMP;
	}

	public void setGenesisTimestamp(long timestamp) {
		this.genesisTimestamp = timestamp;
	}

	public void unsetGenesisTimestamp() {
		this.genesisTimestamp = -1;
	}

}
