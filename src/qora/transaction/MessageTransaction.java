package qora.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Connection;
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
import qora.block.BlockChain;
import qora.crypto.Crypto;
import utils.Base58;
import utils.ParseException;
import utils.Serialization;

public class MessageTransaction extends Transaction {

	// Properties
	protected int version;
	protected PublicKeyAccount sender;
	protected Account recipient;
	protected Long assetId;
	protected BigDecimal amount;
	protected byte[] data;
	protected boolean isText;
	protected boolean isEncrypted;

	// Property lengths
	private static final int SENDER_LENGTH = 32;
	private static final int AMOUNT_LENGTH = 8;
	private static final int ASSET_ID_LENGTH = 8;
	private static final int DATA_SIZE_LENGTH = 4;
	private static final int IS_TEXT_LENGTH = 1;
	private static final int IS_ENCRYPTED_LENGTH = 1;
	private static final int TYPELESS_DATALESS_LENGTH_V1 = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH + DATA_SIZE_LENGTH
			+ IS_TEXT_LENGTH + IS_ENCRYPTED_LENGTH;
	private static final int TYPELESS_DATALESS_LENGTH_V3 = BASE_TYPELESS_LENGTH + SENDER_LENGTH + RECIPIENT_LENGTH + ASSET_ID_LENGTH + AMOUNT_LENGTH
			+ DATA_SIZE_LENGTH + IS_TEXT_LENGTH + IS_ENCRYPTED_LENGTH;

	// Other property lengths
	private static final int MAX_DATA_SIZE = 4000;

	// Constructors
	public MessageTransaction(PublicKeyAccount sender, String recipient, Long assetId, BigDecimal amount, BigDecimal fee, byte[] data, boolean isText,
			boolean isEncrypted, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.MESSAGE, fee, sender, timestamp, reference, signature);

		this.version = Transaction.getVersionByTimestamp(this.timestamp);
		this.sender = sender;
		this.recipient = new Account(recipient);

		if (assetId != null)
			this.assetId = assetId;
		else
			this.assetId = Asset.QORA;

		this.amount = amount;
		this.data = data;
		this.isText = isText;
		this.isEncrypted = isEncrypted;
	}

	// Getters/Setters

	public int getVersion() {
		return this.version;
	}

	public Account getSender() {
		return this.sender;
	}

	public Account getRecipient() {
		return this.recipient;
	}

	public Long getAssetId() {
		return this.assetId;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public byte[] getData() {
		return this.data;
	}

	public boolean isText() {
		return this.isText;
	}

	public boolean isEncrypted() {
		return this.isEncrypted;
	}

	// More information

	public int getDataLength() {
		if (this.version == 1)
			return TYPE_LENGTH + TYPELESS_DATALESS_LENGTH_V1 + this.data.length;
		else
			return TYPE_LENGTH + TYPELESS_DATALESS_LENGTH_V3 + this.data.length;
	}

	// Load/Save

	/**
	 * Load MessageTransaction from DB using signature.
	 * 
	 * @param signature
	 * @throws NoDataFoundException
	 *             if no matching row found
	 * @throws SQLException
	 */
	protected MessageTransaction(byte[] signature) throws SQLException {
		super(TransactionType.MESSAGE, signature);

		ResultSet rs = DB.checkedExecute(
				"SELECT version, sender, recipient, is_text, is_encrypted, amount, asset_id, data FROM MessageTransactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		this.version = rs.getInt(1);
		this.sender = new PublicKeyAccount(DB.getResultSetBytes(rs.getBinaryStream(2), CREATOR_LENGTH));
		this.recipient = new Account(rs.getString(3));
		this.isText = rs.getBoolean(4);
		this.isEncrypted = rs.getBoolean(5);
		this.amount = rs.getBigDecimal(6).setScale(8);
		this.assetId = rs.getLong(7);
		this.data = DB.getResultSetBytes(rs.getBinaryStream(8));
	}

	/**
	 * Load MessageTransaction from DB using signature
	 * 
	 * @param signature
	 * @return MessageTransaction, or null if not found
	 * @throws SQLException
	 */
	public static MessageTransaction fromSignature(byte[] signature) throws SQLException {
		try {
			return new MessageTransaction(signature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	@Override
	public void save(Connection connection) throws SQLException {
		super.save(connection);

		SaveHelper saveHelper = new SaveHelper(connection, "MessageTransactions");
		saveHelper.bind("signature", this.signature).bind("version", this.version).bind("sender", this.sender.getPublicKey())
				.bind("recipient", this.recipient.getAddress()).bind("is_text", this.isText).bind("is_encrypted", this.isEncrypted).bind("amount", this.amount)
				.bind("asset_id", this.assetId).bind("data", this.data);
		saveHelper.execute();
	}

	// Converters

	protected static Transaction parse(ByteBuffer byteBuffer) throws ParseException {
		if (byteBuffer.remaining() < TIMESTAMP_LENGTH)
			throw new ParseException("Byte data too short for MessageTransaction");

		long timestamp = byteBuffer.getLong();
		int version = Transaction.getVersionByTimestamp(timestamp);

		int minimumRemaining = version == 1 ? TYPELESS_DATALESS_LENGTH_V1 : TYPELESS_DATALESS_LENGTH_V3;
		minimumRemaining -= TIMESTAMP_LENGTH; // Already read above

		if (byteBuffer.remaining() < minimumRemaining)
			throw new ParseException("Byte data too short for MessageTransaction");

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);
		PublicKeyAccount sender = Serialization.deserializePublicKey(byteBuffer);
		String recipient = Serialization.deserializeRecipient(byteBuffer);

		long assetId;
		if (version == 1)
			assetId = Asset.QORA;
		else
			assetId = byteBuffer.getLong();

		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		int dataSize = byteBuffer.getInt(0);
		// Don't allow invalid dataSize here to avoid run-time issues
		if (dataSize > MAX_DATA_SIZE)
			throw new ParseException("MessageTransaction data size too large");

		byte[] data = new byte[dataSize];
		byteBuffer.get(data);

		boolean isEncrypted = byteBuffer.get() != 0;
		boolean isText = byteBuffer.get() != 0;

		BigDecimal fee = Serialization.deserializeBigDecimal(byteBuffer);
		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		return new MessageTransaction(sender, recipient, assetId, amount, fee, data, isText, isEncrypted, timestamp, reference, signature);
	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject toJSON() throws SQLException {
		JSONObject json = getBaseJSON();

		json.put("version", this.version);
		json.put("sender", this.sender.getAddress());
		json.put("senderPublicKey", HashCode.fromBytes(this.sender.getPublicKey()).toString());
		json.put("recipient", this.recipient.getAddress());
		json.put("amount", this.amount.toPlainString());
		json.put("assetId", this.assetId);
		json.put("isText", this.isText);
		json.put("isEncrypted", this.isEncrypted);

		// We can only show plain text as unencoded
		if (this.isText && !this.isEncrypted)
			json.put("data", new String(this.data, Charset.forName("UTF-8")));
		else
			json.put("data", HashCode.fromBytes(this.data).toString());

		return json;
	}

	public byte[] toBytes() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(getDataLength());
			bytes.write(Ints.toByteArray(this.type.value));
			bytes.write(Longs.toByteArray(this.timestamp));
			bytes.write(this.reference);
			bytes.write(this.sender.getPublicKey());
			bytes.write(Base58.decode(this.recipient.getAddress()));

			if (this.version != 1)
				bytes.write(Longs.toByteArray(this.assetId));

			bytes.write(Serialization.serializeBigDecimal(this.amount));

			bytes.write(Ints.toByteArray(this.data.length));
			bytes.write(this.data);

			bytes.write((byte) (this.isEncrypted ? 1 : 0));
			bytes.write((byte) (this.isText ? 1 : 0));

			bytes.write(Serialization.serializeBigDecimal(this.fee));
			bytes.write(this.signature);
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Processing

	public ValidationResult isValid(Connection connection) throws SQLException {
		// Lowest cost checks first

		// Are message transactions even allowed at this point?
		if (this.version != Transaction.getVersionByTimestamp(this.timestamp))
			return ValidationResult.NOT_YET_RELEASED;

		if (BlockChain.getHeight() < Block.MESSAGE_RELEASE_HEIGHT)
			return ValidationResult.NOT_YET_RELEASED;

		// Check data length
		if (this.data.length < 1 || this.data.length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check recipient is a valid address
		if (!Crypto.isValidAddress(this.recipient.getAddress()))
			return ValidationResult.INVALID_ADDRESS;

		if (this.version == 1) {
			// Check amount is positive (V1)
			if (this.amount.compareTo(BigDecimal.ZERO) <= 0)
				return ValidationResult.NEGATIVE_AMOUNT;
		} else {
			// Check amount is not negative (V3) as sending messages without a payment is OK
			if (this.amount.compareTo(BigDecimal.ZERO) < 0)
				return ValidationResult.NEGATIVE_AMOUNT;
		}

		// Check fee is positive
		if (this.fee.compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check reference is correct
		if (!Arrays.equals(this.sender.getLastReference(), this.reference))
			return ValidationResult.INVALID_REFERENCE;

		// Does asset exist? (This test not present in gen1)
		if (this.assetId != Asset.QORA && !Asset.exists(this.assetId))
			return ValidationResult.ASSET_DOES_NOT_EXIST;

		// If asset is QORA then we need to check amount + fee in one go
		if (this.assetId == Asset.QORA) {
			// Check sender has enough funds for amount + fee in QORA
			if (this.sender.getBalance(Asset.QORA, 1).compareTo(this.amount.add(this.fee)) == -1)
				return ValidationResult.NO_BALANCE;
		} else {
			// Check sender has enough funds for amount in whatever asset
			if (this.sender.getBalance(this.assetId, 1).compareTo(this.amount) == -1)
				return ValidationResult.NO_BALANCE;

			// Check sender has enough funds for fee in QORA
			if (this.sender.getBalance(Asset.QORA, 1).compareTo(this.fee) == -1)
				return ValidationResult.NO_BALANCE;
		}

		return ValidationResult.OK;
	}

	public void process(Connection connection) throws SQLException {
		this.save(connection);

		// Update sender's balance due to amount
		this.sender.setConfirmedBalance(connection, this.assetId, this.sender.getConfirmedBalance(this.assetId).subtract(this.amount));
		// Update sender's balance due to fee
		this.sender.setConfirmedBalance(connection, Asset.QORA, this.sender.getConfirmedBalance(Asset.QORA).subtract(this.fee));

		// Update recipient's balance
		this.recipient.setConfirmedBalance(connection, this.assetId, this.recipient.getConfirmedBalance(this.assetId).add(this.amount));

		// Update sender's reference
		this.sender.setLastReference(connection, this.signature);

		// For QORA amounts only: if recipient has no reference yet, then this is their starting reference
		if (this.assetId == Asset.QORA && this.recipient.getLastReference() == null)
			this.recipient.setLastReference(connection, this.signature);
	}

	public void orphan(Connection connection) throws SQLException {
		this.delete(connection);

		// Update sender's balance due to amount
		this.sender.setConfirmedBalance(connection, this.assetId, this.sender.getConfirmedBalance(this.assetId).add(this.amount));
		// Update sender's balance due to fee
		this.sender.setConfirmedBalance(connection, Asset.QORA, this.sender.getConfirmedBalance(Asset.QORA).add(this.fee));

		// Update recipient's balance
		this.recipient.setConfirmedBalance(connection, this.assetId, this.recipient.getConfirmedBalance(this.assetId).subtract(this.amount));

		// Update sender's reference
		this.sender.setLastReference(connection, this.reference);

		/*
		 * For QORA amounts only: If recipient's last reference is this transaction's signature, then they can't have made any transactions of their own (which
		 * would have changed their last reference) thus this is their first reference so remove it.
		 */
		if (this.assetId == Asset.QORA && Arrays.equals(this.recipient.getLastReference(), this.signature))
			this.recipient.setLastReference(connection, null);
	}

}
