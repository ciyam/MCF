package qora.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import org.json.simple.JSONObject;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import database.DB;
import database.NoDataFoundException;
import qora.account.Account;
import qora.account.GenesisAccount;
import qora.account.PrivateKeyAccount;
import qora.assets.Asset;
import qora.crypto.Crypto;
import repository.hsqldb.HSQLDBSaver;
import transform.TransformationException;
import utils.Base58;
import utils.Serialization;

public class GenesisTransaction extends TransactionHandler {

	// Properties
	private Account recipient;
	private BigDecimal amount;

	// Property lengths
	private static final int RECIPIENT_LENGTH = 25; // raw, not Base58-encoded
	private static final int AMOUNT_LENGTH = 8;
	// Note that Genesis transactions don't require reference, fee or signature:
	private static final int TYPELESS_LENGTH = TIMESTAMP_LENGTH + RECIPIENT_LENGTH + AMOUNT_LENGTH;

	// Constructors

	public GenesisTransaction(String recipient, BigDecimal amount, long timestamp) {
		super(TransactionType.GENESIS, BigDecimal.ZERO, new GenesisAccount(), timestamp, null, null);

		this.recipient = new Account(recipient);
		this.amount = amount;
		this.signature = calcSignature();
	}

	// Getters/Setters

	public Account getRecipient() {
		return this.recipient;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	// More information

	@Override
	public int getDataLength() {
		return TYPE_LENGTH + TYPELESS_LENGTH;
	}

	// Load/Save

	/**
	 * Load GenesisTransaction from DB using signature.
	 * 
	 * @param signature
	 * @throws NoDataFoundException
	 *             if no matching row found
	 * @throws SQLException
	 */
	protected GenesisTransaction(byte[] signature) throws SQLException {
		super(TransactionType.GENESIS, signature);

		ResultSet rs = DB.checkedExecute("SELECT recipient, amount FROM GenesisTransactions WHERE signature = ?", signature);
		if (rs == null)
			throw new NoDataFoundException();

		this.recipient = new Account(rs.getString(1));
		this.amount = rs.getBigDecimal(2).setScale(8);
	}

	/**
	 * Load GenesisTransaction from DB using signature
	 * 
	 * @param signature
	 * @return GenesisTransaction, or null if not found
	 * @throws SQLException
	 */
	public static GenesisTransaction fromSignature(byte[] signature) throws SQLException {
		try {
			return new GenesisTransaction(signature);
		} catch (NoDataFoundException e) {
			return null;
		}
	}

	@Override
	public void save() throws SQLException {
		super.save();

		HSQLDBSaver saveHelper = new HSQLDBSaver("GenesisTransactions");
		saveHelper.bind("signature", this.signature).bind("recipient", this.recipient.getAddress()).bind("amount", this.amount);
		saveHelper.execute();
	}

	// Converters

	protected static TransactionHandler parse(ByteBuffer byteBuffer) throws TransformationException {
		if (byteBuffer.remaining() < TYPELESS_LENGTH)
			throw new TransformationException("Byte data too short for GenesisTransaction");

		long timestamp = byteBuffer.getLong();
		String recipient = Serialization.deserializeRecipient(byteBuffer);
		BigDecimal amount = Serialization.deserializeBigDecimal(byteBuffer);

		return new GenesisTransaction(recipient, amount, timestamp);
	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject toJSON() throws SQLException {
		JSONObject json = getBaseJSON();

		json.put("recipient", this.recipient.getAddress());
		json.put("amount", this.amount.toPlainString());

		return json;
	}

	@Override
	public byte[] toBytes() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(getDataLength());
			bytes.write(Ints.toByteArray(this.type.value));
			bytes.write(Longs.toByteArray(this.timestamp));
			bytes.write(Base58.decode(this.recipient.getAddress()));
			bytes.write(Serialization.serializeBigDecimal(this.amount));
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Processing

	/**
	 * Refuse to calculate genesis transaction signature!
	 * <p>
	 * This is not possible as there is no private key for the genesis account and so no way to sign data.
	 * <p>
	 * <b>Always throws IllegalStateException.</b>
	 * 
	 * @throws IllegalStateException
	 */
	@Override
	public byte[] calcSignature(PrivateKeyAccount signer) {
		throw new IllegalStateException("There is no private key for genesis transactions");
	}

	/**
	 * Generate genesis transaction signature.
	 * <p>
	 * This is handled differently as there is no private key for the genesis account and so no way to sign data.
	 * <p>
	 * Instead we return the SHA-256 digest of the transaction, duplicated so that the returned byte[] is the same length as normal transaction signatures.
	 * 
	 * @return byte[]
	 */
	private byte[] calcSignature() {
		byte[] digest = Crypto.digest(toBytes());
		return Bytes.concat(digest, digest);
	}

	/**
	 * Check validity of genesis transaction signature.
	 * <p>
	 * This is handled differently as there is no private key for the genesis account and so no way to sign/verify data.
	 * <p>
	 * Instead we compared our signature with one generated by {@link GenesisTransaction#calcSignature()}.
	 * 
	 * @return boolean
	 */
	@Override
	public boolean isSignatureValid() {
		return Arrays.equals(this.signature, calcSignature());
	}

	@Override
	public ValidationResult isValid() {
		// Check amount is zero or positive
		if (this.amount.compareTo(BigDecimal.ZERO) == -1)
			return ValidationResult.NEGATIVE_AMOUNT;

		// Check recipient address is valid
		if (!Crypto.isValidAddress(this.recipient.getAddress()))
			return ValidationResult.INVALID_ADDRESS;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws SQLException {
		this.save();

		// Set recipient's balance
		this.recipient.setConfirmedBalance(Asset.QORA, this.amount);

		// Set recipient's reference
		recipient.setLastReference(this.signature);
	}

	@Override
	public void orphan() throws SQLException {
		this.delete();

		// Reset recipient's balance
		this.recipient.deleteBalance(Asset.QORA);

		// Set recipient's reference
		recipient.setLastReference(null);
	}

}
