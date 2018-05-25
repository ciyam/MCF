package test;

import static org.junit.Assert.*;

import java.sql.SQLException;

import org.junit.Test;

import qora.block.Block;
import qora.block.BlockChain;
import qora.transaction.PaymentTransaction;
import utils.Base58;

public class navigation extends common {

	@Test
	public void testNavigateFromTransactionToBlock() throws SQLException {
		assertTrue("Migrate old database to at least block 49778 before running this test", BlockChain.getHeight() >= 49778);

		String signature58 = "1211ZPwG3hk5evWzXCZi9hMDRpwumWmkENjwWkeTCik9xA5uoYnxzF7rwR5hmHH3kG2RXo7ToCAaRc7dvnynByJt";
		byte[] signature = Base58.decode(signature58);

		System.out.println("Navigating to Block from transaction " + signature58);

		PaymentTransaction paymentTransaction = PaymentTransaction.fromSignature(signature);
		assertNotNull("Payment transaction not loaded from database", paymentTransaction);

		Block block = paymentTransaction.getBlock();
		assertNotNull("Block 49778 not loaded from database", block);

		System.out.println("Block " + block.getHeight() + ", signature: " + Base58.encode(block.getSignature()));

		assertEquals(49778, block.getHeight());
	}

}
