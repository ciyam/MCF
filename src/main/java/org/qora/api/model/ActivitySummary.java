package org.qora.api.model;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.qora.api.TransactionCountMapXmlAdapter;
import org.qora.transaction.Transaction.TransactionType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ActivitySummary {

	public int blockCount;
	public int transactionCount;
	public int assetsIssued;
	public int namesRegistered;

	// Assuming TransactionType values are contiguous so 'length' equals count
	@XmlJavaTypeAdapter(TransactionCountMapXmlAdapter.class)
	public Map<TransactionType, Integer> transactionCountByType = new HashMap<>();

	public ActivitySummary() {
	}

}
