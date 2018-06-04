package qora.assets;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import database.DB;
import database.NoDataFoundException;
import database.SaveHelper;
import qora.account.PublicKeyAccount;
import qora.transaction.Transaction;

/*
 * TODO:
 * Probably need to standardize on using assetId or assetKey for the long value, and plain "asset" for the java object.
 * Thus in the database the primary key column could be called "asset_id".
 * In the Order object, we'd pass longs to variables with names like "haveAssetId" and use getters like "getHaveAssetId"
 * which frees up other method names like "getHaveAsset" to return a java Asset object. 
 */

public class Asset {

	public static final long QORA = 0L;

	// Properties
	private Long assetId;
	private PublicKeyAccount owner;
	private String name;
	private String description;
	private long quantity;
	private boolean isDivisible;
	private byte[] reference;

	// Property lengths
	private static final int OWNER_LENGTH = Transaction.CREATOR_LENGTH;

	// NOTE: key is Long because it can be null if asset ID/key not yet assigned (which is done by save() method).
	public Asset(Long assetId, PublicKeyAccount owner, String name, String description, long quantity, boolean isDivisible, byte[] reference) {
		this.assetId = assetId;
		this.owner = owner;
		this.name = name;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
		this.reference = reference;
	}

	// New asset with unassigned assetId
	public Asset(PublicKeyAccount owner, String name, String description, long quantity, boolean isDivisible, byte[] reference) {
		this(null, owner, name, description, quantity, isDivisible, reference);
	}

	// Load/Save/Delete/Exists

	protected Asset(long assetId) throws SQLException {
		this(DB.checkedExecute("SELECT owner, asset_name, description, quantity, is_divisible, reference FROM Assets WHERE asset_id = ?", assetId));
	}

	protected Asset(ResultSet rs) throws SQLException {
		if (rs == null)
			throw new NoDataFoundException();

		this.owner = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(1), OWNER_LENGTH));
		this.name = rs.getString(2);
		this.description = rs.getString(3);
		this.quantity = rs.getLong(4);
		this.isDivisible = rs.getBoolean(5);
		this.reference = DB.getResultSetBytes(rs.getBinaryStream(6), Transaction.REFERENCE_LENGTH);
	}

	public static Asset fromAssetId(long assetId) throws SQLException {
		try {
			return new Asset(assetId);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	public void save(Connection connection) throws SQLException {
		SaveHelper saveHelper = new SaveHelper(connection, "Assets");
		saveHelper.bind("asset_id", this.assetId).bind("owner", this.owner.getAddress()).bind("asset_name", this.name).bind("description", this.description)
				.bind("quantity", this.quantity).bind("is_divisible", this.isDivisible).bind("reference", this.reference);
		saveHelper.execute();

		if (this.assetId == null)
			this.assetId = DB.callIdentity(connection);
	}

	public static boolean exists(long assetId) throws SQLException {
		return DB.exists("Assets", "asset_id = ?", assetId);
	}

}
