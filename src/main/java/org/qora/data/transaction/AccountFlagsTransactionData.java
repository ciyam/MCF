package org.qora.data.transaction;

import java.math.BigDecimal;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qora.account.GenesisAccount;
import org.qora.block.GenesisBlock;
import org.qora.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = {TransactionData.class})
//JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("ACCOUNT_FLAGS")
public class AccountFlagsTransactionData extends TransactionData {

	private String target;
	private int andMask;
	private int orMask;
	private int xorMask;
	private Integer previousFlags;

	// Constructors

	// For JAXB
	protected AccountFlagsTransactionData() {
		super(TransactionType.ACCOUNT_FLAGS);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		/*
		 *  If we're being constructed as part of the genesis block info inside blockchain config
		 *  and no specific creator's public key is supplied
		 *  then use genesis account's public key.
		 */
		if (parent instanceof GenesisBlock.GenesisInfo && this.creatorPublicKey == null)
			this.creatorPublicKey = GenesisAccount.PUBLIC_KEY;
	}

	public AccountFlagsTransactionData(long timestamp, int groupId, byte[] reference, byte[] creatorPublicKey, String target, int andMask, int orMask,
			int xorMask, Integer previousFlags, BigDecimal fee, byte[] signature) {
		super(TransactionType.ACCOUNT_FLAGS, timestamp, groupId, reference, creatorPublicKey, fee, signature);

		this.target = target;
		this.andMask = andMask;
		this.orMask = orMask;
		this.xorMask = xorMask;
		this.previousFlags = previousFlags;
	}

	// Typically used in deserialization context
	public AccountFlagsTransactionData(long timestamp, int groupId, byte[] reference, byte[] creatorPublicKey, String target, int andMask, int orMask,
			int xorMask, BigDecimal fee, byte[] signature) {
		this(timestamp, groupId, reference, creatorPublicKey, target, andMask, orMask, xorMask, null, fee, signature);
	}

	// Getters / setters

	public String getTarget() {
		return this.target;
	}

	public int getAndMask() {
		return this.andMask;
	}

	public int getOrMask() {
		return this.orMask;
	}

	public int getXorMask() {
		return this.xorMask;
	}

	public Integer getPreviousFlags() {
		return this.previousFlags;
	}

	public void setPreviousFlags(Integer previousFlags) {
		this.previousFlags = previousFlags;
	}

	// Re-expose to JAXB

	@Override
	@XmlElement
	public byte[] getCreatorPublicKey() {
		return super.getCreatorPublicKey();
	}

	@Override
	@XmlElement
	public void setCreatorPublicKey(byte[] creatorPublicKey) {
		super.setCreatorPublicKey(creatorPublicKey);
	}

}
