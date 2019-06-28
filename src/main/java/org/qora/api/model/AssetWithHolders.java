package org.qora.api.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.qora.data.account.AccountBalanceData;
import org.qora.data.asset.AssetData;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Asset info, maybe including asset holders")
// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class AssetWithHolders {

	@Schema(implementation = AssetData.class, name = "asset", title = "asset data")
	@XmlElement(name = "asset")
	public AssetData assetData;

	public List<AccountBalanceData> holders;

	// For JAX-RS
	protected AssetWithHolders() {
	}

	public AssetWithHolders(AssetData assetData, List<AccountBalanceData> holders) {
		this.assetData = assetData;
		this.holders = holders;
	}

}
