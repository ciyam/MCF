package data.at;

import java.math.BigDecimal;

public class ATStateData {

	// Properties
	private String ATAddress;
	private Integer height;
	private Long creation;
	private byte[] stateData;
	private byte[] stateHash;
	private BigDecimal fees;

	// Constructors

	/** Create new ATStateData */
	public ATStateData(String ATAddress, Integer height, Long creation, byte[] stateData, byte[] stateHash, BigDecimal fees) {
		this.ATAddress = ATAddress;
		this.height = height;
		this.creation = creation;
		this.stateData = stateData;
		this.stateHash = stateHash;
		this.fees = fees;
	}

	/** For recreating per-block ATStateData from repository where not all info is needed */
	public ATStateData(String ATAddress, int height, byte[] stateHash, BigDecimal fees) {
		this(ATAddress, height, null, null, stateHash, fees);
	}

	/** For creating ATStateData from serialized bytes when we don't have all the info */
	public ATStateData(String ATAddress, byte[] stateHash) {
		this(ATAddress, null, null, null, stateHash, null);
	}

	/** For creating ATStateData from serialized bytes when we don't have all the info */
	public ATStateData(String ATAddress, byte[] stateHash, BigDecimal fees) {
		this(ATAddress, null, null, null, stateHash, fees);
	}

	// Getters / setters

	public String getATAddress() {
		return this.ATAddress;
	}

	public Integer getHeight() {
		return this.height;
	}

	// Likely to be used when block received over network is attached to blockchain
	public void setHeight(Integer height) {
		this.height = height;
	}

	public Long getCreation() {
		return this.creation;
	}

	public byte[] getStateData() {
		return this.stateData;
	}

	public byte[] getStateHash() {
		return this.stateHash;
	}

	public BigDecimal getFees() {
		return this.fees;
	}

}
