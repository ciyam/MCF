package data.at;

import java.math.BigDecimal;

public class ATData {

	// Properties
	private String ATAddress;
	private int version;
	private byte[] codeBytes;
	private boolean isSleeping;
	private Integer sleepUntilHeight;
	private boolean isFinished;
	private boolean hadFatalError;
	private boolean isFrozen;
	private BigDecimal frozenBalance;
	private byte[] deploySignature;

	// Constructors

	public ATData(String ATAddress, int version, byte[] codeBytes, boolean isSleeping, Integer sleepUntilHeight, boolean isFinished, boolean hadFatalError,
			boolean isFrozen, BigDecimal frozenBalance, byte[] deploySignature) {
		this.ATAddress = ATAddress;
		this.version = version;
		this.codeBytes = codeBytes;
		this.isSleeping = isSleeping;
		this.sleepUntilHeight = sleepUntilHeight;
		this.isFinished = isFinished;
		this.hadFatalError = hadFatalError;
		this.isFrozen = isFrozen;
		this.frozenBalance = frozenBalance;
		this.deploySignature = deploySignature;
	}

	public ATData(String ATAddress, int version, byte[] codeBytes, boolean isSleeping, Integer sleepUntilHeight, boolean isFinished, boolean hadFatalError,
			boolean isFrozen, Long frozenBalance, byte[] deploySignature) {
		this(ATAddress, version, codeBytes, isSleeping, sleepUntilHeight, isFinished, hadFatalError, isFrozen, (BigDecimal) null, deploySignature);

		// Convert Long frozenBalance to BigDecimal
		if (frozenBalance != null)
			this.frozenBalance = BigDecimal.valueOf(frozenBalance).setScale(8).divide(BigDecimal.valueOf(1e8));
	}

	// Getters / setters

	public String getATAddress() {
		return this.ATAddress;
	}

	public int getVersion() {
		return this.version;
	}

	public byte[] getCodeBytes() {
		return this.codeBytes;
	}

	public boolean getIsSleeping() {
		return this.isSleeping;
	}

	public void setIsSleeping(boolean isSleeping) {
		this.isSleeping = isSleeping;
	}

	public Integer getSleepUntilHeight() {
		return this.sleepUntilHeight;
	}

	public void setSleepUntilHeight(Integer sleepUntilHeight) {
		this.sleepUntilHeight = sleepUntilHeight;
	}

	public boolean getIsFinished() {
		return this.isFinished;
	}

	public void setIsFinished(boolean isFinished) {
		this.isFinished = isFinished;
	}

	public boolean getHadFatalError() {
		return this.hadFatalError;
	}

	public void setHadFatalError(boolean hadFatalError) {
		this.hadFatalError = hadFatalError;
	}

	public boolean getIsFrozen() {
		return this.isFrozen;
	}

	public void setIsFrozen(boolean isFrozen) {
		this.isFrozen = isFrozen;
	}

	public BigDecimal getFrozenBalance() {
		return this.frozenBalance;
	}

	public void setFrozenBalance(BigDecimal frozenBalance) {
		this.frozenBalance = frozenBalance;
	}

	public byte[] getDeploySignature() {
		return this.deploySignature;
	}

}
