package qora.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import org.json.simple.JSONObject;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import database.DB;
import database.NoDataFoundException;
import database.SaveHelper;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.Block;
import qora.crypto.Crypto;
import utils.Base58;
import utils.NTP;
import utils.ParseException;
import utils.Serialization;

public class IssueAssetTransaction extends Transaction {

	// Properties
	private PublicKeyAccount issuer;
	private Account owner;
	private String assetName;
	private String description;
	private long quantity;
	private boolean isDivisible;
	// assetId assigned during save() or during load from database
	private Long assetId = null;

	// Property lengths
	private static final int ISSUER_LENGTH = CREATOR_LENGTH;
	private static final int OWNER_LENGTH = RECIPIENT_LENGTH;
	private static final int NAME_SIZE_LENGTH = 4;
	private static final int DESCRIPTION_SIZE_LENGTH = 4;
	private static final int QUANTITY_LENGTH = 8;
	private static final int IS_DIVISIBLE_LENGTH = 1;
	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + ISSUER_LENGTH + OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH
			+ QUANTITY_LENGTH + IS_DIVISIBLE_LENGTH;

	// Other useful lengths
	private static final int MAX_NAME_SIZE = 400;
	private static final int MAX_DESCRIPTION_SIZE = 4000;

	// Constructors

	/**
	 * Reconstruct an IssueAssetTransaction, including signature.
	 * 
	 * @param issuer
	 * @param owner
	 * @param assetName
	 * @param description
	 * @param quantity
	 * @param isDivisible
	 * @param fee
	 * @param timestamp
	 * @param reference
	 * @param signature
	 */
	public IssueAssetTransaction(PublicKeyAccount issuer, String owner, String assetName, String description, long quantity, boolean isDivisible,
			BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.ISSUE_ASSET, fee, issuer, timestamp, reference, signature);

		this.issuer = issuer;
		this.owner = new Account(owner);
		this.assetName = assetName;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
	}

	/**
	 * Construct a new IssueAssetTransaction.
	 * 
	 * @param issuer
	 * @param owner
	 * @param assetName
	 * @param description
	 * @param quantity
	 * @param isDivisible
	 * @param fee
	 * @param timestamp
	 * @param reference
	 */
	public IssueAssetTransaction(PublicKeyAccount issuer, String owner, String assetName, String description, long quantity, boolean isDivisible,
			BigDecimal fee, long timestamp, byte[] reference) {
		this(issuer, owner, assetName, description, quantity, isDivisible, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public PublicKeyAccount getIssuer() {
		return this.issuer;
	}

	public Account getOwner() {
		return this.owner;
	}

	public String getAssetName() {
		return this.assetName;
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

	// More information

	/**
	 * Return asset ID assigned if this transaction has been processed.
	 * 
	 * @return asset ID if transaction has been processed and asset created, null otherwise
	 */
	public Long getAssetId() {
		return this.assetId;
	}

	public int getDataLength() {
		return TYPE_LENGTH + TYPELESS_LENGTH + assetName.length() + description.length();
	}

	// Load/Save

	/**
	 * Construct IssueAssetTransaction from DB using signature.
	 * 
	 * @param signature
	 * @throws NoDataFoundException
	 *             if no matching row found
	 * @throws SQLException
	 */
	protected IssueAssetTransaction(byte[] signature) throws SQLException {
		super(TransactionType.ISSUE_ASSET, signature);

		ResultSet rs = DB.checkedExecute(
				"SELECT issuer, owner, asset_name, description, quantity, is_divisible, asset_id FROM IssueAssetTransactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		this.issuer = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(2), ISSUER_LENGTH));
		this.owner = new Account(rs.getString(2));
		this.assetName = rs.getString(3);
		this.description = rs.getString(4);
		this.quantity = rs.getLong(5);
		this.isDivisible = rs.getBoolean(6);
		this.assetId = rs.getLong(7);
	}

	/**
	 * Load IssueAssetTransaction from DB using signature.
	 * 
	 * @param signature
	 * @return PaymentTransaction, or null if not found
	 * @throws SQLException
	 */
	public static IssueAssetTransaction fromSignature(byte[] signature) throws SQLException {
		try {
			return new IssueAssetTransaction(signature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	@Override
	public void save() throws SQLException {
		super.save();

		SaveHelper saveHelper = new SaveHelper("IssueAssetTransactions");
		saveHelper.bind("signature", this.signature).bind("creator", this.creator.getPublicKey()).bind("asset_name", this.assetName)
				.bind("description", this.description).bind("quantity", this.quantity).bind("is_divisible", this.isDivisible).bind("asset_id", this.assetId);
		saveHelper.execute();
	}

	// Converters

	protected static Transaction parse(ByteBuffer byteBuffer) throws ParseException {
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new ParseException("Byte data too short for IssueAssetTransaction");

		long timestamp = byteBuffer.getLong();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		PublicKeyAccount issuer = Serialization.deserializePublicKey(byteBuffer);
		String owner = Serialization.deserializeRecipient(byteBuffer);

		String assetName = Serialization.deserializeSizedString(byteBuffer, MAX_NAME_SIZE);
		String description = Serialization.deserializeSizedString(byteBuffer, MAX_DESCRIPTION_SIZE);

		// Still need to make sure there are enough bytes left for remaining fields
		if (byteBuffer.remaining() < QUANTITY_LENGTH + IS_DIVISIBLE_LENGTH + SIGNATURE_LENGTH)
			throw new ParseException("Byte data too short for IssueAssetTransaction");

		long quantity = byteBuffer.getLong();
		boolean isDivisible = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new IssueAssetTransaction(issuer, owner, assetName, description, quantity, isDivisible, fee, timestamp, reference, signature);
	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject toJSON() throws SQLException {
		JSONObject json = getBaseJSON();

		json.put("issuer", this.creator.getAddress());
		json.put("issuerPublicKey", HashCode.fromBytes(this.creator.getPublicKey()).toString());
		json.put("owner", this.owner.getAddress());
		json.put("assetName", this.assetName);
		json.put("description", this.description);
		json.put("quantity", this.quantity);
		json.put("isDivisible", this.isDivisible);

		return json;
	}

	public byte[] toBytes() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(getDataLength());
			bytes.write(Ints.toByteArray(this.type.value));
			bytes.write(Longs.toByteArray(this.timestamp));
			bytes.write(this.reference);
			bytes.write(this.issuer.getPublicKey());
			bytes.write(Base58.decode(this.owner.getAddress()));

			bytes.write(Ints.toByteArray(this.assetName.length()));
			bytes.write(this.assetName.getBytes("UTF-8"));

			bytes.write(Ints.toByteArray(this.description.length()));
			bytes.write(this.description.getBytes("UTF-8"));

			bytes.write(Longs.toByteArray(this.quantity));
			bytes.write((byte) (this.isDivisible ? 1 : 0));

			bytes.write(this.signature);
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Processing

	public ValidationResult isValid() throws SQLException {
		// Lowest cost checks first

		// Are IssueAssetTransactions even allowed at this point?
		if (NTP.getTime() < Block.ASSETS_RELEASE_TIMESTAMP)
			return ValidationResult.NOT_YET_RELEASED;

		// Check owner address is valid
		if (!Crypto.isValidAddress(this.owner.getAddress()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		if (this.assetName.length() < 1 || this.assetName.length() > MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		if (this.description.length() < 1 || this.description.length() > MAX_NAME_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check quantity - either 10 billion or if that's not enough: a billion billion!
		long maxQuantity = this.isDivisible ? 10_000_000_000L : 1_000_000_000_000_000_000L;
		if (this.quantity < 1 || this.quantity > maxQuantity)
			return ValidationResult.INVALID_QUANTITY;

		// Check fee is positive
		if (this.fee.compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		if (!Arrays.equals(this.issuer.getLastReference(), this.reference))
			return ValidationResult.INVALID_REFERENCE;

		// Check issuer has enough funds
		if (this.issuer.getConfirmedBalance(Asset.QORA).compareTo(this.fee) == -1)
			return ValidationResult.NO_BALANCE;

		// XXX: Surely we want to check the asset name isn't already taken?
		if (Asset.exists(this.assetName))
			return ValidationResult.ASSET_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	public void process() throws SQLException {
		// Issue asset
		Asset asset = new Asset(owner.getAddress(), this.assetName, this.description, this.quantity, this.isDivisible, this.reference);
		asset.save();

		// Note newly assigned asset ID in our transaction record
		this.assetId = asset.getAssetId();

		this.save();

		// Update issuer's balance
		this.issuer.setConfirmedBalance(Asset.QORA, this.issuer.getConfirmedBalance(Asset.QORA).subtract(this.fee));

		// Update issuer's reference
		this.issuer.setLastReference(this.signature);

		// Add asset to owner
		this.owner.setConfirmedBalance(this.assetId, BigDecimal.valueOf(this.quantity).setScale(8));
	}

	public void orphan() throws SQLException {
		// Remove asset from owner
		this.owner.deleteBalance(this.assetId);

		// Unissue asset
		Asset asset = Asset.fromAssetId(this.assetId);
		asset.delete();

		this.delete();

		// Update issuer's balance
		this.issuer.setConfirmedBalance(Asset.QORA, this.issuer.getConfirmedBalance(Asset.QORA).add(this.fee));

		// Update issuer's reference
		this.issuer.setLastReference(this.reference);
	}

}
