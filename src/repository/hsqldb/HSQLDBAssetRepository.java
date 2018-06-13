package repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;

import data.assets.AssetData;
import repository.AssetRepository;
import repository.DataException;

public class HSQLDBAssetRepository implements AssetRepository {

	protected HSQLDBRepository repository;

	public HSQLDBAssetRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	public AssetData fromAssetId(long assetId) throws DataException {
		try {
			ResultSet resultSet = this.repository
					.checkedExecute("SELECT owner, asset_name, description, quantity, is_divisible, reference FROM Assets WHERE asset_id = ?", assetId);
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String assetName = resultSet.getString(2);
			String description = resultSet.getString(3);
			long quantity = resultSet.getLong(4);
			boolean isDivisible = resultSet.getBoolean(5);
			byte[] reference = this.repository.getResultSetBytes(resultSet.getBinaryStream(6));

			return new AssetData(assetId, owner, assetName, description, quantity, isDivisible, reference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset from repository", e);
		}
	}

	public boolean assetExists(long assetId) throws DataException {
		try {
			return this.repository.exists("Assets", "asset_id = ?", assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	public boolean assetExists(String assetName) throws DataException {
		try {
			return this.repository.exists("Assets", "asset_name = ?", assetName);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	public void save(AssetData assetData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Assets");
		saveHelper.bind("asset_id", assetData.getAssetId()).bind("owner", assetData.getOwner()).bind("asset_name", assetData.getName())
				.bind("description", assetData.getDescription()).bind("quantity", assetData.getQuantity()).bind("is_divisible", assetData.getIsDivisible())
				.bind("reference", assetData.getReference());

		try {
			saveHelper.execute(this.repository);

			if (assetData.getAssetId() == null)
				assetData.setAssetId(this.repository.callIdentity());
		} catch (SQLException e) {
			throw new DataException("Unable to save asset into repository", e);
		}
	}

	public void delete(long assetId) throws DataException {
		try {
			this.repository.checkedExecute("DELETE FROM Assets WHERE assetId = ?", assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset from repository", e);
		}
	}

}
