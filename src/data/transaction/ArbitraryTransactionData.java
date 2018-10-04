package data.transaction;

import java.math.BigDecimal;
import java.util.List;

import data.PaymentData;
import qora.transaction.Transaction.TransactionType;

public class ArbitraryTransactionData extends TransactionData {

	// "data" field types
	public enum DataType {
		RAW_DATA,
		DATA_HASH;
	}

	// Properties
	private int version;
	private byte[] senderPublicKey;
	private int service;
	private byte[] data;
	private DataType dataType;
	private List<PaymentData> payments;

	// Constructors

	/** Reconstructing a V3 arbitrary transaction with signature */
	public ArbitraryTransactionData(int version, byte[] senderPublicKey, int service, byte[] data, DataType dataType, List<PaymentData> payments,
			BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(TransactionType.ARBITRARY, fee, senderPublicKey, timestamp, reference, signature);

		this.version = version;
		this.senderPublicKey = senderPublicKey;
		this.service = service;
		this.data = data;
		this.dataType = dataType;
		this.payments = payments;
	}

	/** Constructing a new V3 arbitrary transaction without signature */
	public ArbitraryTransactionData(int version, byte[] senderPublicKey, int service, byte[] data, DataType dataType, List<PaymentData> payments,
			BigDecimal fee, long timestamp, byte[] reference) {
		this(version, senderPublicKey, service, data, dataType, payments, fee, timestamp, reference, null);
	}

	/** Reconstructing a V1 arbitrary transaction with signature */
	public ArbitraryTransactionData(int version, byte[] senderPublicKey, int service, byte[] data, DataType dataType, BigDecimal fee, long timestamp,
			byte[] reference, byte[] signature) {
		this(version, senderPublicKey, service, data, dataType, null, fee, timestamp, reference, signature);
	}

	/** Constructing a new V1 arbitrary transaction without signature */
	public ArbitraryTransactionData(int version, byte[] senderPublicKey, int service, byte[] data, DataType dataType, BigDecimal fee, long timestamp,
			byte[] reference) {
		this(version, senderPublicKey, service, data, dataType, null, fee, timestamp, reference, null);
	}

	// Getters/Setters

	public int getVersion() {
		return this.version;
	}

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public int getService() {
		return this.service;
	}

	public byte[] getData() {
		return this.data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public DataType getDataType() {
		return this.dataType;
	}

	public void setDataType(DataType dataType) {
		this.dataType = dataType;
	}

	public List<PaymentData> getPayments() {
		return this.payments;
	}

}
