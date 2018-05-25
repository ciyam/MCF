package qora.assets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import database.DB;
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
		String sql = DB.formatInsertWithPlaceholders("Assets", "asset", "owner", "asset_name", "description", "quantity", "is_divisible", "reference");
		PreparedStatement preparedStatement = connection.prepareStatement(sql);
		DB.bindInsertPlaceholders(preparedStatement, this.key, this.owner.getAddress(), this.name, this.description, this.quantity, this.isDivisible,
				this.reference);
		preparedStatement.execute();

		if (this.key == null)
			this.key = DB.callIdentity(connection);
	}
}
