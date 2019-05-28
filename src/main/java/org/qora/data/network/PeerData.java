package org.qora.data.network;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import org.qora.network.PeerAddress;

import io.swagger.v3.oas.annotations.media.Schema;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class PeerData {

	// Properties

	// Don't expose this via JAXB - use pretty getter instead
	@XmlTransient
	@Schema(hidden = true)
	private PeerAddress peerAddress;
	private Long lastAttempted;
	private Long lastConnected;
	private Integer lastHeight;
	private byte[] lastBlockSignature;
	private Long lastBlockTimestamp;
	private byte[] lastBlockGenerator;
	private Long lastMisbehaved;

	// Constructors

	// necessary for JAXB serialization
	protected PeerData() {
	}

	public PeerData(PeerAddress peerAddress, Long lastAttempted, Long lastConnected, Integer lastHeight, byte[] lastBlockSignature, Long lastBlockTimestamp, byte[] lastBlockGenerator, Long lastMisbehaved) {
		this.peerAddress = peerAddress;
		this.lastAttempted = lastAttempted;
		this.lastConnected = lastConnected;
		this.lastHeight = lastHeight;
		this.lastBlockSignature = lastBlockSignature;
		this.lastBlockTimestamp = lastBlockTimestamp;
		this.lastBlockGenerator = lastBlockGenerator;
		this.lastMisbehaved = lastMisbehaved;
	}

	public PeerData(PeerAddress peerAddress) {
		this(peerAddress, null, null, null, null, null, null, null);
	}

	// Getters / setters

	// Don't let JAXB use this getter
	@XmlTransient
	@Schema(hidden = true)
	public PeerAddress getAddress() {
		return this.peerAddress;
	}

	public Long getLastAttempted() {
		return this.lastAttempted;
	}

	public void setLastAttempted(Long lastAttempted) {
		this.lastAttempted = lastAttempted;
	}

	public Long getLastConnected() {
		return this.lastConnected;
	}

	public void setLastConnected(Long lastConnected) {
		this.lastConnected = lastConnected;
	}

	public Integer getLastHeight() {
		return this.lastHeight;
	}

	public void setLastHeight(Integer lastHeight) {
		this.lastHeight = lastHeight;
	}

	public byte[] getLastBlockSignature() {
		return lastBlockSignature;
	}

	public void setLastBlockSignature(byte[] lastBlockSignature) {
		this.lastBlockSignature = lastBlockSignature;
	}

	public Long getLastBlockTimestamp() {
		return lastBlockTimestamp;
	}

	public void setLastBlockTimestamp(Long lastBlockTimestamp) {
		this.lastBlockTimestamp = lastBlockTimestamp;
	}

	public byte[] getLastBlockGenerator() {
		return lastBlockGenerator;
	}

	public void setLastBlockGenerator(byte[] lastBlockGenerator) {
		this.lastBlockGenerator = lastBlockGenerator;
	}

	public Long getLastMisbehaved() {
		return this.lastMisbehaved;
	}

	public void setLastMisbehaved(Long lastMisbehaved) {
		this.lastMisbehaved = lastMisbehaved;
	}

	// Pretty peerAddress getter for JAXB
	@XmlElement(name = "address")
	protected String getPrettyAddress() {
		return this.peerAddress.toString();
	}

}
