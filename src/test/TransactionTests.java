package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.hash.HashCode;

import data.account.AccountBalanceData;
import data.account.AccountData;
import data.block.BlockData;
import data.transaction.PaymentTransactionData;
import qora.account.Account;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.Block;
import qora.block.BlockChain;
import qora.transaction.PaymentTransaction;
import qora.transaction.Transaction;
import qora.transaction.Transaction.ValidationResult;
import repository.AccountRepository;
import repository.DataException;
import repository.Repository;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;
import utils.NTP;

// Don't extend Common as we want to use an in-memory database
public class TransactionTests {

	private static final String connectionUrl = "jdbc:hsqldb:mem:db/test;create=true;close_result=true;sql.strict_exec=true;sql.enforce_names=true;sql.syntax_mys=true";

	private static final byte[] generatorSeed = HashCode.fromString("0123456789abcdeffedcba98765432100123456789abcdeffedcba9876543210").asBytes();
	private static final byte[] senderSeed = HashCode.fromString("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef").asBytes();
	private static final byte[] recipientSeed = HashCode.fromString("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210").asBytes();

	@BeforeClass
	public static void setRepository() throws DataException {
		RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
		RepositoryManager.setRepositoryFactory(repositoryFactory);
	}

	@AfterClass
	public static void closeRepository() throws DataException {
		RepositoryManager.closeRepositoryFactory();
	}

	@Test
	public void testPaymentTransactions() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			assertEquals("Blockchain should be empty for this test", 0, repository.getBlockRepository().getBlockchainHeight());
		}

		// This needs to be called outside of acquiring our own repository or it will deadlock
		BlockChain.validate();

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Grab genesis block
			BlockData genesisBlockData = repository.getBlockRepository().fromHeight(1);

			AccountRepository accountRepository = repository.getAccountRepository();

			// Create test generator account
			BigDecimal generatorBalance = BigDecimal.valueOf(1_000_000_000L);
			PrivateKeyAccount generator = new PrivateKeyAccount(repository, generatorSeed);
			accountRepository.save(new AccountData(generator.getAddress(), generatorSeed));
			accountRepository.save(new AccountBalanceData(generator.getAddress(), Asset.QORA, generatorBalance));

			// Create test sender account
			PrivateKeyAccount sender = new PrivateKeyAccount(repository, senderSeed);

			// Mock account
			byte[] reference = senderSeed;
			accountRepository.save(new AccountData(sender.getAddress(), reference));

			// Mock balance
			BigDecimal initialBalance = BigDecimal.valueOf(1_000_000L);
			accountRepository.save(new AccountBalanceData(sender.getAddress(), Asset.QORA, initialBalance));

			repository.saveChanges();

			// Make a new payment transaction
			Account recipient = new PublicKeyAccount(repository, recipientSeed);
			BigDecimal amount = BigDecimal.valueOf(1_000L);
			BigDecimal fee = BigDecimal.ONE;
			long timestamp = genesisBlockData.getTimestamp() + 1_000;
			PaymentTransactionData paymentTransactionData = new PaymentTransactionData(sender.getPublicKey(), recipient.getAddress(), amount, fee, timestamp,
					reference);

			Transaction paymentTransaction = new PaymentTransaction(repository, paymentTransactionData);
			paymentTransaction.calcSignature(sender);
			assertTrue(paymentTransaction.isSignatureValid());
			assertEquals(ValidationResult.OK, paymentTransaction.isValid());

			// Forge new block with payment transaction
			Block block = new Block(repository, genesisBlockData, generator, null, null);
			block.addTransaction(paymentTransactionData);
			block.sign();

			assertTrue("Block signatures invalid", block.isSignatureValid());
			assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

			block.process();
			repository.saveChanges();

			// Check sender's balance
			BigDecimal expectedBalance = initialBalance.subtract(amount).subtract(fee);
			BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
			assertTrue("Sender's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

			// Fee should be in generator's balance
			expectedBalance = generatorBalance.add(fee);
			actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
			assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

			// Amount should be in recipient's balance
			expectedBalance = amount;
			actualBalance = accountRepository.getBalance(recipient.getAddress(), Asset.QORA).getBalance();
			assertTrue("Recipient's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);
		}
	}

}