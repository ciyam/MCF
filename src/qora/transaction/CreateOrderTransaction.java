package qora.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.simple.JSONObject;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import database.DB;
import database.NoDataFoundException;
import qora.account.PublicKeyAccount;
import qora.assets.Order;
import repository.hsqldb.HSQLDBSaver;
import transform.TransformationException;

public class CreateOrderTransaction extends Transaction {

	// Properties
	private Order order;

	// Property lengths
	private static final int ASSET_LENGTH = 8;
	private static final int AMOUNT_LENGTH = 12;
	private static final int TYPELESS_LENGTH = BASE_TYPELESS_LENGTH + CREATOR_LENGTH + (ASSET_LENGTH + AMOUNT_LENGTH) * 2;

	// Constructors

	public CreateOrderTransaction(PublicKeyAccount creator, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price, BigDecimal fee,
			long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.CREATE_ASSET_ORDER, fee, creator, timestamp, reference, signature);

		this.order = new Order(new BigInteger(this.signature), creator, haveAssetId, wantAssetId, amount, price, timestamp);
	}

	public CreateOrderTransaction(PublicKeyAccount creator, long haveAssetId, long wantAssetId, BigDecimal amount, BigDecimal price, BigDecimal fee,
			long timestamp, byte[] reference) {
		this(creator, haveAssetId, wantAssetId, amount, price, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public Order getOrder() {
		return this.order;
	}

	// More information

	public int getDataLength() {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	// Load/Save

	/**
	 * Load CreateOrderTransaction from DB using signature.
	 * 
	 * @param signature
	 * @throws NoDataFoundException
	 *             if no matching row found
	 * @throws SQLException
	 */
	protected CreateOrderTransaction(byte[] signature) throws SQLException {
		super(TransactionType.CREATE_ASSET_ORDER, signature);

		ResultSet rs = DB.checkedExecute("SELECT have_asset_id, amount, want_asset_id, price FROM CreateOrderTransactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		long haveAssetId = rs.getLong(1);
		BigDecimal amount = rs.getBigDecimal(2);
		long wantAssetId = rs.getLong(3);
		BigDecimal price = rs.getBigDecimal(4);

		this.order = new Order(new BigInteger(this.signature), this.creator, haveAssetId, wantAssetId, amount, price, this.timestamp);
	}

	/**
	 * Load CreateOrderTransaction from DB using signature
	 * 
	 * @param signature
	 * @return CreateOrderTransaction, or null if not found
	 * @throws SQLException
	 */
	public static CreateOrderTransaction fromSignature(byte[] signature) throws SQLException {
		try {
			return new CreateOrderTransaction(signature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	@Override
	public void save() throws SQLException {
		super.save();

		HSQLDBSaver saveHelper = new HSQLDBSaver("CreateAssetOrderTransactions");
		saveHelper.bind("signature", this.signature).bind("creator", this.creator.getPublicKey()).bind("have_asset_id", this.order.getHaveAssetId())
				.bind("amount", this.order.getAmount()).bind("want_asset_id", this.order.getWantAssetId()).bind("price", this.order.getPrice());
		saveHelper.execute();
	}

	// Converters

	protected static TransactionHandler parse(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new TransformationException("Byte data too short for CreateOrderTransaction");

		// TODO
		return null;
	}

	@Override
	public JSONObject toJSON() throws SQLException {
		JSONObject json = getBaseJSON();

		// TODO

		return json;
	}

	public byte[] toBytes() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(getDataLength());
			bytes.write(Ints.toByteArray(this.type.value));
			bytes.write(Longs.toByteArray(this.timestamp));
			bytes.write(this.reference);

			// TODO

			bytes.write(this.signature);
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Processing

	public ValidationResult isValid() throws SQLException {
		// TODO

		return ValidationResult.OK;
	}

	public void process() throws SQLException {
		this.save();

		// TODO
	}

	public void orphan() throws SQLException {
		this.delete();

		// TODO
	}

}
