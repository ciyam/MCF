package org.qora.test.common;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class AccountUtils {

	public static Map<String, Map<Long, BigDecimal>> getBalances(Repository repository, long... assetIds) throws DataException {
		Map<String, Map<Long, BigDecimal>> balances = new HashMap<>();

		for (TestAccount account : Common.getTestAccounts(repository))
			for (Long assetId : assetIds) {
				BigDecimal balance = account.getConfirmedBalance(assetId);

				balances.compute(account.accountName, (key, value) -> {
					if (value == null)
						value = new HashMap<Long, BigDecimal>();

					value.put(assetId, balance);

					return value;
				});
			}

		return balances;
	}

}
