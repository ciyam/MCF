package org.qora.test.assets;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qora.asset.Order;
import org.qora.repository.DataException;
import org.qora.test.common.Common;

public class GranularityTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	/**
	 * Check granularity adjustment values.
	 */
	@Test
	public void testGranularities() {
		// Price 1/12 is rounded down to 0.08333333.
		// To keep [divisible] amount * 0.08333333 to nearest 0.00000001 then amounts need to be multiples of 1.00000000.
		testGranularity(true, true, "1", "12", "1");

		// Any amount * 12 will be valid for divisible asset so granularity is 0.00000001
		testGranularity(true, true, "12", "1", "0.00000001");

		// Price 1/10 is 0.10000000.
		// To keep amount * 0.1 to nearest 1 then amounts need to be multiples of 10.
		testGranularity(false, false, "1", "10", "10");

		// Price is 50307/123 which is 409
		// Any [indivisible] amount * 409 will be valid for divisible asset to granularity is 1
		testGranularity(false, false, "50307", "123", "1");

		// Price 1/800 is 0.00125000
		// Amounts are indivisible so must be integer.
		// Return-amounts are divisible and can be fractional.
		// So even though amount needs to be multiples of 1.00000000,
		// return-amount will always end up being valid.
		// Thus at price 0.00125000 we expect granularity to be 1
		testGranularity(false, true, "1", "800", "1");

		// Price 1/800 is 0.00125000
		// Amounts are divisible so can be fractional.
		// Return-amounts are indivisible so must be integer.
		// So even though amount can be multiples of 0.00000001,
		// return-amount needs to be multiples of 1.00000000
		// Thus at price 0.00125000 we expect granularity to be 800
		testGranularity(true, false, "1", "800", "800");

		// Price 800
		// Amounts are indivisible so must be integer.
		// Return-amounts are divisible so can be fractional.
		// So even though amount needs to be multiples of 1.00000000,
		// return-amount will always end up being valid.
		// Thus at price 800 we expect granularity to be 1
		testGranularity(false, true, "800", "1", "1");

		// Price 800
		// Amounts are divisible so can be fractional.
		// Return-amounts are indivisible so must be integer.
		// So even though amount can be multiples of 0.00000001,
		// return-amount needs to be multiples of 1.00000000
		// Thus at price 800 we expect granularity to be 0.00125000
		testGranularity(true, false, "800", "1", "0.00125000");
	}

	private void testGranularity(boolean isAmountAssetDivisible, boolean isReturnAssetDivisible, String dividend, String divisor, String expectedGranularity) {
		final BigDecimal price = new BigDecimal(dividend).setScale(8).divide(new BigDecimal(divisor).setScale(8), RoundingMode.DOWN);

		BigDecimal granularity = Order.calculateAmountGranularity(isAmountAssetDivisible, isReturnAssetDivisible, price);
		assertEqualBigDecimals("Granularity incorrect", new BigDecimal(expectedGranularity), granularity);
	}

}
