package qora.assets;

import data.assets.AssetData;
import repository.Repository;

public class Asset {

	/**
	 * QORA coins are just another asset but with fixed assetId of zero.
	 */
	public static final long QORA = 0L;

	// Properties
	private Repository repository;
	private AssetData assetData;

	// Constructors

	public Asset(Repository repository, AssetData assetData) {
		this.repository = repository;
		this.assetData = assetData;
	}

}
