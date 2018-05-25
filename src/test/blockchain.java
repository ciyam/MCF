package test;

import java.sql.SQLException;

import org.junit.Test;

import qora.block.BlockChain;

public class blockchain extends common {

	@Test
	public void testRebuild() throws SQLException {
		BlockChain.validate();
	}

}
