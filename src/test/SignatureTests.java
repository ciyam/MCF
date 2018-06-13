package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import org.junit.Test;

import data.block.BlockData;
import qora.account.PrivateKeyAccount;
import qora.block.Block;
import qora.block.GenesisBlock;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import utils.Base58;
import utils.NTP;

public class SignatureTests extends Common {

	@Test
	public void testGenesisBlockSignature() throws DataException {
		String expected58 = "6pHMBFif7jXFG654joT8GPaymau1fMtaxacRyqSrnAwQMQDvqRuLpHpfFyqX4gWVvj4pF1mwQhFgqWAvjVvPJUjmBZQvL751dM9cEcQBTaUcxtNLuWZCVUAtbnWN9f7FsLppHhkPbxwpoodL3UJYRGt3EZrG17mhv1RJbmq8j6rr7Mk";

		try (final Repository repository = RepositoryManager.getRepository()) {
			GenesisBlock block = new GenesisBlock(repository);
			BlockData blockData = block.getBlockData();

			System.out
					.println("Generator: " + block.getGenerator().getAddress() + ", generator signature: " + Base58.encode(blockData.getGeneratorSignature()));

			assertEquals(expected58, Base58.encode(block.getSignature()));
		}
	}

	@Test
	public void testBlockSignature() throws DataException {
		int version = 3;

		byte[] reference = Base58.decode(
				"BSfgEr6r1rXGGJCv8criR5NcBWfpHdJnm9x5unPwxvojEKCESv1wH1zJm7yvCeC48wshymYtARbHdUojbqWCCWW7h2UTc8g5oEx59C9M41dM7H48My8gVkcEZdxR1of3VgpE5UcowFp3kFC12hVcD9hUttJ2i2nZWMwprbFtUGyVv1U");

		long timestamp = NTP.getTime() - 5000;

		BigDecimal generatingBalance = BigDecimal.valueOf(10_000_000L).setScale(8);

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount generator = new PrivateKeyAccount(repository,
					new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 });

			byte[] atBytes = null;

			BigDecimal atFees = null;

			Block block = new Block(repository, version, reference, timestamp, generatingBalance, generator, atBytes, atFees);

			block.calcGeneratorSignature();
			block.calcTransactionsSignature();

			assertTrue(block.isSignatureValid());
		}
	}

}
