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

	public static BigDecimal getBalance(Repository repository, String accountName, long assetId) throws DataException {
		return Common.getTestAccount(repository, accountName).getConfirmedBalance(assetId);
	}

	public static void assertBalance(Repository repository, String accountName, long assetId, BigDecimal expectedBalance) throws DataException {
		BigDecimal actualBalance = getBalance(repository, accountName, assetId);
		String assetName = repository.getAssetRepository().fromAssetId(assetId).getName();

		Common.assertEqualBigDecimals(String.format("%s's %s [%d] balance incorrect", accountName, assetName, assetId), expectedBalance, actualBalance);
	}

}
