package org.qora.data.transaction;

import java.math.BigDecimal;
import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qora.data.PaymentData;
import org.qora.transaction.Transaction.ApprovalStatus;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
//JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("ARBITRARY")
public class ArbitraryTransactionData extends TransactionData {

	// "data" field types
	@Schema(accessMode = AccessMode.READ_ONLY)
	public enum DataType {
		RAW_DATA,
		DATA_HASH;
	}

	// Properties
	private int version;

	@Schema(example = "sender_public_key")
	private byte[] senderPublicKey;

	private int service;

	@Schema(example = "raw_data_in_base58")
	private byte[] data;
	private DataType dataType;
	private List<PaymentData> payments;

	// Constructors

	// For JAXB
	protected ArbitraryTransactionData() {
		super(TransactionType.ARBITRARY);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	/** From repository */
	public ArbitraryTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, int version, int service,
			byte[] data, DataType dataType, List<PaymentData> payments, BigDecimal fee, ApprovalStatus approvalStatus, Integer height, byte[] signature) {
		super(TransactionType.ARBITRARY, timestamp, txGroupId, reference, senderPublicKey, fee, approvalStatus, height, signature);

		this.senderPublicKey = senderPublicKey;
		this.version = version;
		this.service = service;
		this.data = data;
		this.dataType = dataType;
		this.payments = payments;
	}

	/** From network/API (V3) */
	public ArbitraryTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, int version, int service,
			byte[] data, DataType dataType, List<PaymentData> payments, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, senderPublicKey, version, service, data, dataType, payments, fee, null, null, signature);
	}

	/** From network/API (V1) */
	public ArbitraryTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, int version, int service, byte[] data,
			DataType dataType, BigDecimal fee, byte[] signature) {
		this(timestamp, txGroupId, reference, senderPublicKey, version, service, data, dataType, null, fee, null, null, signature);
	}

	/** New, unsigned (V3) */
	public ArbitraryTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, int version, int service, byte[] data,
			DataType dataType, List<PaymentData> payments, BigDecimal fee) {
		this(timestamp, txGroupId, reference, senderPublicKey, version, service, data, dataType, payments, fee, null);
	}

	/** New, unsigned (V1) */
	public ArbitraryTransactionData(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, int version, int service, byte[] data,
			DataType dataType, BigDecimal fee) {
		this(timestamp, txGroupId, reference, senderPublicKey, version, service, data, dataType, null, fee, null);
	}

	// Getters/Setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public int getVersion() {
		return this.version;
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
