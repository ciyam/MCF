package qora.assets;

import java.sql.ResultSet;
import java.sql.SQLException;

import database.DB;
import database.NoDataFoundException;
import qora.account.Account;
import repository.hsqldb.HSQLDBSaver;

public class Asset {

	public static final long QORA = 0L;

	// Properties
	private Long assetId;
	private Account owner;
	private String name;
	private String description;
	private long quantity;
	private boolean isDivisible;
	private byte[] reference;

	// NOTE: key is Long because it can be null if asset ID/key not yet assigned (which is done by save() method).
	public Asset(Long assetId, String owner, String name, String description, long quantity, boolean isDivisible, byte[] reference) {
		this.assetId = assetId;
		this.owner = new Account(owner);
		this.name = name;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
		this.reference = reference;
	}

	// New asset with unassigned assetId
	public Asset(String owner, String name, String description, long quantity, boolean isDivisible, byte[] reference) {
		this(null, owner, name, description, quantity, isDivisible, reference);
	}

	// Getters/Setters

	public Long getAssetId() {
		return this.assetId;
	}

	public Account getOwner() {
		return this.owner;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public long getQuantity() {
		return this.quantity;
	}

	public boolean isDivisible() {
		return this.isDivisible;
	}

	public byte[] getReference() {
		return this.reference;
	}

	// Load/Save/Delete/Exists

	protected Asset(long assetId) throws SQLException {
		this(DB.checkedExecute("SELECT owner, asset_name, description, quantity, is_divisible, reference FROM Assets WHERE asset_id = ?", assetId));
	}

	protected Asset(ResultSet rs) throws SQLException {
		if (rs == null)
			throw new NoDataFoundException();

		this.owner = new Account(rs.getString(1));
		this.name = rs.getString(2);
		this.description = rs.getString(3);
		this.quantity = rs.getLong(4);
		this.isDivisible = rs.getBoolean(5);
		this.reference = DB.getResultSetBytes(rs.getBinaryStream(6));
	}

	public static Asset fromAssetId(long assetId) throws SQLException {
		try {
			return new Asset(assetId);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	public void save() throws SQLException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Assets");
		saveHelper.bind("asset_id", this.assetId).bind("owner", this.owner.getAddress()).bind("asset_name", this.name).bind("description", this.description)
				.bind("quantity", this.quantity).bind("is_divisible", this.isDivisible).bind("reference", this.reference);
		saveHelper.execute();

		if (this.assetId == null)
			this.assetId = DB.callIdentity();
	}

	public void delete() throws SQLException {
		DB.checkedExecute("DELETE FROM Assets WHERE asset_id = ?", this.assetId);
	}

	public static boolean exists(long assetId) throws SQLException {
		return DB.exists("Assets", "asset_id = ?", assetId);
	}

	public static boolean exists(String assetName) throws SQLException {
		return DB.exists("Assets", "asset_name = ?", assetName);
	}

}
