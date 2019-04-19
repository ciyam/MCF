package org.qora.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class BlockForgeSummary {

	public String address;
	public int blockCount;

	protected BlockForgeSummary() {
	}

	public BlockForgeSummary(String address, int blockCount) {
		this.address = address;
		this.blockCount = blockCount;
	}

}
