package data.at;

public class ATStateData {

	// Properties
	private String ATAddress;
	private int height;
	private byte[] stateData;

	// Constructors

	public ATStateData(String ATAddress, int height, byte[] stateData) {
		this.ATAddress = ATAddress;
		this.height = height;
		this.stateData = stateData;
	}

	// Getters / setters

	public String getATAddress() {
		return this.ATAddress;
	}

	public int getHeight() {
		return this.height;
	}

	public byte[] getStateData() {
		return this.stateData;
	}

}
