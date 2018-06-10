package data.transaction;

import java.math.BigDecimal;
import java.util.Map;

import data.account.PublicKeyAccount;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public abstract class Transaction {

	// Transaction types
	// TODO Transaction types are semantic and should go into the business logic layer.
	// No need to know the meaning of the integer value in data layer
	public enum TransactionType {
		GENESIS(1), PAYMENT(2), REGISTER_NAME(3), UPDATE_NAME(4), SELL_NAME(5), CANCEL_SELL_NAME(6), BUY_NAME(7), CREATE_POLL(8), VOTE_ON_POLL(9), ARBITRARY(
				10), ISSUE_ASSET(11), TRANSFER_ASSET(12), CREATE_ASSET_ORDER(13), CANCEL_ASSET_ORDER(14), MULTIPAYMENT(15), DEPLOY_AT(16), MESSAGE(17);

		public final int value;

		private final static Map<Integer, TransactionType> map = stream(TransactionType.values()).collect(toMap(type -> type.value, type -> type));

		TransactionType(int value) {
			this.value = value;
		}

		public static TransactionType valueOf(int value) {
			return map.get(value);
		}
	}

	// Properties shared with all transaction types
	protected TransactionType type;	
	// TODO PublicKeyAccount is a separate data entity, so here should only be a key to reference it 
	protected PublicKeyAccount creator;
	protected long timestamp;
	protected byte[] reference;
	protected BigDecimal fee;
	protected byte[] signature;

	// Constructors

	public Transaction(TransactionType type, BigDecimal fee, PublicKeyAccount creator, long timestamp, byte[] reference, byte[] signature) {
		this.fee = fee;
		this.type = type;
		this.creator = creator;
		this.timestamp = timestamp;
		this.reference = reference;
		this.signature = signature;
	}

	public Transaction(TransactionType type, BigDecimal fee, PublicKeyAccount creator, long timestamp, byte[] reference) {
		this(type, fee, creator, timestamp, reference, null);
	}

	// Getters/setters

	public TransactionType getType() {
		return this.type;
	}

	public PublicKeyAccount getCreator() {
		return this.creator;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public BigDecimal getFee() {
		return this.fee;
	}

	public byte[] getSignature() {
		return this.signature;
	}

}
