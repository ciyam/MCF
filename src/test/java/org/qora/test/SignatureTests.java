package org.qora.test;

import org.junit.Test;
import org.qora.account.PrivateKeyAccount;
import org.qora.block.Block;
import org.qora.block.GenesisBlock;
import org.qora.data.block.BlockData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.utils.Base58;

import static org.junit.Assert.*;

import java.math.BigDecimal;

public class SignatureTests extends Common {

	@Test
	public void testGenesisBlockSignature() throws DataException {
		String expected58 = "6pHMBFif7jXFG654joT8GPaymau1fMtaxacRyqSrnAwQMQDvqRuLpHpfFyqX4gWVvj4pF1mwQhFgqWAvjVvPJUjmBZQvL751dM9cEcQBTaUcxtNLuWZCVUAtbnWN9f7FsLppHhkPbxwpoodL3UJYRGt3EZrG17mhv1RJbmq8j6rr7Mk";

		try (final Repository repository = RepositoryManager.getRepository()) {
			GenesisBlock block = GenesisBlock.getInstance(repository);
			BlockData blockData = block.getBlockData();

			System.out
					.println("Generator: " + block.getGenerator().getAddress() + ", generator signature: " + Base58.encode(blockData.getGeneratorSignature()));

			assertEquals(expected58, Base58.encode(block.getSignature()));
		}
	}

	@Test
	public void testBlockSignature() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount generator = new PrivateKeyAccount(repository,
					new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 });

			int version = 3;

			byte[] reference = Base58.decode(
					"BSfgEr6r1rXGGJCv8criR5NcBWfpHdJnm9x5unPwxvojEKCESv1wH1zJm7yvCeC48wshymYtARbHdUojbqWCCWW7h2UTc8g5oEx59C9M41dM7H48My8gVkcEZdxR1of3VgpE5UcowFp3kFC12hVcD9hUttJ2i2nZWMwprbFtUGyVv1U");

			int transactionCount = 0;
			BigDecimal totalFees = BigDecimal.ZERO.setScale(8);
			byte[] transactionsSignature = null;
			int height = 0;
			long timestamp = System.currentTimeMillis() - 5000;
			BigDecimal generatingBalance = BigDecimal.valueOf(10_000_000L).setScale(8);
			byte[] generatorPublicKey = generator.getPublicKey();
			byte[] generatorSignature = null;
			int atCount = 0;
			BigDecimal atFees = BigDecimal.valueOf(10_000_000L).setScale(8);

			BlockData blockData = new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, generatingBalance,
					generatorPublicKey, generatorSignature, atCount, atFees);

			Block block = new Block(repository, blockData, generator, timestamp + 1);
			block.sign();

			assertTrue(block.isSignatureValid());
		}
	}

}
