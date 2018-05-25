package test;

import static org.junit.Assert.*;

import java.sql.SQLException;

import org.junit.Test;

import qora.block.GenesisBlock;
import utils.Base58;

public class signatures extends common {

	@Test
	public void testGenesisBlockSignature() throws SQLException {
		String expected58 = "6pHMBFif7jXFG654joT8GPaymau1fMtaxacRyqSrnAwQMQDvqRuLpHpfFyqX4gWVvj4pF1mwQhFgqWAvjVvPJUjmBZQvL751dM9cEcQBTaUcxtNLuWZCVUAtbnWN9f7FsLppHhkPbxwpoodL3UJYRGt3EZrG17mhv1RJbmq8j6rr7Mk";

		GenesisBlock block = GenesisBlock.getInstance();

		System.out.println("Generator: " + block.getGenerator().getAddress() + ", generator signature: " + Base58.encode(block.getGeneratorSignature()));

		assertEquals(expected58, Base58.encode(block.getSignature()));
	}

}
