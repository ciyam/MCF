package api.models;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import api.ApiError;
import api.ApiErrorFactory;
import data.account.AccountBalanceData;
import data.assets.AssetData;
import io.swagger.v3.oas.annotations.media.Schema;
import repository.DataException;
import repository.Repository;

@Schema(description = "Asset with (optional) asset holders")
public class AssetWithHolders {

	@Schema(implementation = AssetData.class, name = "asset", title = "asset data")
	@XmlElement(name = "asset")
	public AssetData assetData;

	public List<AccountBalanceData> holders;

	// For JAX-RS
	@SuppressWarnings("unused")
	private AssetWithHolders() {
	}

	public AssetWithHolders(Repository repository, AssetData assetData, boolean includeHolders) throws DataException {
		if (assetData == null)
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_ASSET_ID);

		this.assetData = assetData;

		if (includeHolders)
			this.holders = repository.getAccountRepository().getAssetBalances(assetData.getAssetId());
	}

}
