package org.qora.api;

import javax.xml.bind.Unmarshaller.Listener;

import org.qora.data.transaction.TransactionData;

public class UnmarshalListener extends Listener {

	@Override
	public void afterUnmarshal(Object target, Object parent) {
		if (!(target instanceof TransactionData))
			return;

		// do something
		return;
	}

}
