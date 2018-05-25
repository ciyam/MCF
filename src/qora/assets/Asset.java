package qora.assets;

import java.sql.Connection;
import java.sql.SQLException;

import database.DB;
import database.SaveHelper;
import qora.account.PublicKeyAccount;

public class Asset {

	public static final long QORA = 0L;

	// Properties
	private Long key;
	private PublicKeyAccount owner;
	private String name;
	private String description;
	private long quantity;
	private boolean isDivisible;
	private byte[] reference;

	public Asset(Long key, PublicKeyAccount owner, String name, String description, long quantity, boolean isDivisible, byte[] reference) {
		this.key = key;
		this.owner = owner;
		this.name = name;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
		this.reference = reference;
	}

	public Asset(PublicKeyAccount owner, String name, String description, long quantity, boolean isDivisible, byte[] reference) {
		this(null, owner, name, description, quantity, isDivisible, reference);
	}

	// Load/Save

	public void save(Connection connection) throws SQLException {
		SaveHelper saveHelper = new SaveHelper(connection, "Assets");
		saveHelper.bind("asset", this.key).bind("owner", this.owner.getAddress()).bind("asset_name", this.name).bind("description", this.description)
				.bind("quantity", this.quantity).bind("is_divisible", this.isDivisible).bind("reference", this.reference);
		saveHelper.execute();

		if (this.key == null)
			this.key = DB.callIdentity(connection);
	}
}
