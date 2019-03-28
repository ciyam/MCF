package org.qora.test.assets;

import org.junit.Before;
import org.junit.Test;
import org.qora.asset.Asset;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.Common;
import org.qora.test.common.AssetUtils;

import static org.junit.Assert.*;

import java.math.BigDecimal;

public class TradingTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.resetBlockchain();
	}

	/*
	 * Check full matching of orders with prices that
	 * can't be represented in floating binary.
	 * 
	 * For example, sell 1 GOLD for 12 QORA so
	 * price is 1/12 or 0.083...
	 */
	@Test
	public void testNonExactFraction() throws DataException {
		final long qoraAmount = 24L;
		final long otherAmount = 2L;

		final long transferAmount = 100L;

		try (Repository repository = RepositoryManager.getRepository()) {
			// Create initial order
			AssetUtils.createOrder(repository, "main", Asset.QORA, AssetUtils.testAssetId, qoraAmount, otherAmount);

			// Give 100 asset to other account so they can create order
			AssetUtils.transferAsset(repository, "main", "dummy", AssetUtils.testAssetId, transferAmount);

			// Create matching order
			AssetUtils.createOrder(repository, "dummy", AssetUtils.testAssetId, Asset.QORA, otherAmount, qoraAmount);

			// Check balances to check expected outcome
			BigDecimal actualAmount = Common.getTestAccount(repository, "dummy").getConfirmedBalance(AssetUtils.testAssetId);
			BigDecimal expectedAmount = BigDecimal.valueOf(transferAmount - otherAmount).setScale(8);
			assertTrue("dummy account's asset balance incorrect", actualAmount.compareTo(expectedAmount) == 0);
		}
	}

}