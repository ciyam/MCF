package org.qora.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class NodeInfo {

	public long uptime;
	public String buildVersion;
	public long buildTimestamp;

	public NodeInfo() {
	}

}
