package org.qora.data.transaction;

import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qora.data.PaymentData;
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

	/** V3 */
	public ArbitraryTransactionData(BaseTransactionData baseTransactionData,
			int version, int service, byte[] data, DataType dataType, List<PaymentData> payments) {
		super(TransactionType.ARBITRARY, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.version = version;
		this.service = service;
		this.data = data;
		this.dataType = dataType;
		this.payments = payments;
	}

	/** V1 */
	public ArbitraryTransactionData(BaseTransactionData baseTransactionData,
			int version, int service, byte[] data, DataType dataType) {
		this(baseTransactionData, version, service, data, dataType, null);
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
