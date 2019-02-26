package org.qora.test;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Test;
import org.qora.crosschain.BTC;

import com.google.common.hash.HashCode;

public class BTCTests {

	@Test
	public void testWatchAddress() throws Exception {
		// String testAddress = "mrTDPdM15cFWJC4g223BXX5snicfVJBx6M";
		String testAddress = "1GRENT17xMQe2ukPhwAeZU1TaUUon1Qc65";

		long testStartTime = 1539000000L;

		BTC btc = BTC.getInstance();

		btc.watch(testAddress, testStartTime);

		Thread.sleep(5000);

		btc.watch(testAddress, testStartTime);

		btc.shutdown();
	}

	@Test
	public void testWatchScript() throws Exception {
		long testStartTime = 1539000000L;

		BTC btc = BTC.getInstance();

		byte[] redeemScriptHash = HashCode.fromString("3dbcc35e69ebc449f616fa3eb3723dfad9cbb5b3").asBytes();
		Script redeemScript = ScriptBuilder.createP2SHOutputScript(redeemScriptHash);
		redeemScript.setCreationTimeSeconds(testStartTime);

		// btc.watch(redeemScript);

		Thread.sleep(5000);

		// btc.watch(redeemScript);

		btc.shutdown();
	}

	@Test
	public void updateCheckpoints() throws Exception {
		BTC btc = BTC.getInstance();

		btc.updateCheckpoints();
	}

}
