package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.google.common.hash.HashCode;

import data.account.AccountBalanceData;
import data.account.AccountData;
import data.block.BlockData;
import data.transaction.CreatePollTransactionData;
import data.transaction.PaymentTransactionData;
import data.transaction.VoteOnPollTransactionData;
import data.voting.PollData;
import data.voting.PollOptionData;
import data.voting.VoteOnPollData;
import qora.account.Account;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.Block;
import qora.block.BlockChain;
import qora.transaction.CreatePollTransaction;
import qora.transaction.PaymentTransaction;
import qora.transaction.Transaction;
import qora.transaction.Transaction.ValidationResult;
import qora.transaction.VoteOnPollTransaction;
import repository.AccountRepository;
import repository.DataException;
import repository.Repository;
import repository.RepositoryFactory;
import repository.RepositoryManager;
import repository.hsqldb.HSQLDBRepositoryFactory;
import settings.Settings;

// Don't extend Common as we want to use an in-memory database
public class TransactionTests {

	private static final String connectionUrl = "jdbc:hsqldb:mem:db/test;create=true;close_result=true;sql.strict_exec=true;sql.enforce_names=true;sql.syntax_mys=true";

	private static final byte[] generatorSeed = HashCode.fromString("0123456789abcdeffedcba98765432100123456789abcdeffedcba9876543210").asBytes();
	private static final byte[] senderSeed = HashCode.fromString("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef").asBytes();
	private static final byte[] recipientSeed = HashCode.fromString("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210").asBytes();

	private static final BigDecimal generatorBalance = BigDecimal.valueOf(1_000_000_000L);
	private static final BigDecimal senderBalance = BigDecimal.valueOf(1_000_000L);

	private Repository repository;
	private AccountRepository accountRepository;
	private BlockData genesisBlockData;
	private PrivateKeyAccount sender;
	private PrivateKeyAccount generator;
	private byte[] reference;

	public void createTestAccounts(Long genesisTimestamp) throws DataException {
		RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
		RepositoryManager.setRepositoryFactory(repositoryFactory);

		try (final Repository repository = RepositoryManager.getRepository()) {
			assertEquals("Blockchain should be empty for this test", 0, repository.getBlockRepository().getBlockchainHeight());
		}

		// [Un]set genesis timestamp as required by test
		if (genesisTimestamp != null)
			Settings.getInstance().setGenesisTimestamp(genesisTimestamp);
		else
			Settings.getInstance().unsetGenesisTimestamp();

		// This needs to be called outside of acquiring our own repository or it will deadlock
		BlockChain.validate();

		// Grab repository for further use, including during test itself
		repository = RepositoryManager.getRepository();

		// Grab genesis block
		genesisBlockData = repository.getBlockRepository().fromHeight(1);

		accountRepository = repository.getAccountRepository();

		// Create test generator account
		generator = new PrivateKeyAccount(repository, generatorSeed);
		accountRepository.save(new AccountData(generator.getAddress(), generatorSeed));
		accountRepository.save(new AccountBalanceData(generator.getAddress(), Asset.QORA, generatorBalance));

		// Create test sender account
		sender = new PrivateKeyAccount(repository, senderSeed);

		// Mock account
		reference = senderSeed;
		accountRepository.save(new AccountData(sender.getAddress(), reference));

		// Mock balance
		accountRepository.save(new AccountBalanceData(sender.getAddress(), Asset.QORA, senderBalance));

		repository.saveChanges();
	}

	@After
	public void closeRepository() throws DataException {
		RepositoryManager.closeRepositoryFactory();
	}

	@Test
	public void testPaymentTransaction() throws DataException {
		createTestAccounts(null);

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
		BigDecimal expectedBalance = senderBalance.subtract(amount).subtract(fee);
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

	@Test
	public void testCreatePollTransaction() throws DataException {
		// This test requires GenesisBlock's timestamp is set to something after BlockChain.VOTING_RELEASE_TIMESTAMP
		createTestAccounts(BlockChain.VOTING_RELEASE_TIMESTAMP + 1_000L);

		// Make a new create poll transaction
		String pollName = "test poll";
		String description = "test poll description";

		List<PollOptionData> pollOptions = new ArrayList<PollOptionData>();
		pollOptions.add(new PollOptionData("abort"));
		pollOptions.add(new PollOptionData("retry"));
		pollOptions.add(new PollOptionData("fail"));

		Account recipient = new PublicKeyAccount(repository, recipientSeed);
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = genesisBlockData.getTimestamp() + 1_000;
		CreatePollTransactionData createPollTransactionData = new CreatePollTransactionData(sender.getPublicKey(), recipient.getAddress(), pollName,
				description, pollOptions, fee, timestamp, reference);

		Transaction createPollTransaction = new CreatePollTransaction(repository, createPollTransactionData);
		createPollTransaction.calcSignature(sender);
		assertTrue(createPollTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, createPollTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, genesisBlockData, generator, null, null);
		block.addTransaction(createPollTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check sender's balance
		BigDecimal expectedBalance = senderBalance.subtract(fee);
		BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Fee should be in generator's balance
		expectedBalance = generatorBalance.add(fee);
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Check poll was created
		PollData actualPollData = this.repository.getVotingRepository().fromPollName(pollName);
		assertNotNull(actualPollData);

		// Check sender's reference
		assertTrue("Sender's new reference incorrect", Arrays.equals(createPollTransactionData.getSignature(), sender.getLastReference()));

		// Update reference variable for use by other tests
		reference = sender.getLastReference();
	}

	@Test
	public void testVoteOnPollTransaction() throws DataException {
		// Create poll using another test
		testCreatePollTransaction();

		// Try all options, plus invalid optionIndex (note use of <= for this)
		String pollName = "test poll";
		int pollOptionsSize = 3;
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = genesisBlockData.getTimestamp() + 1_000;
		BlockData previousBlockData = genesisBlockData;

		for (int optionIndex = 0; optionIndex <= pollOptionsSize; ++optionIndex) {
			// Make a vote-on-poll transaction
			VoteOnPollTransactionData voteOnPollTransactionData = new VoteOnPollTransactionData(sender.getPublicKey(), pollName, optionIndex, fee, timestamp,
					reference);

			Transaction voteOnPollTransaction = new VoteOnPollTransaction(repository, voteOnPollTransactionData);
			voteOnPollTransaction.calcSignature(sender);
			assertTrue(voteOnPollTransaction.isSignatureValid());

			if (optionIndex == pollOptionsSize) {
				assertEquals(ValidationResult.POLL_OPTION_DOES_NOT_EXIST, voteOnPollTransaction.isValid());
				break;
			}
			assertEquals(ValidationResult.OK, voteOnPollTransaction.isValid());

			// Forge new block with transaction
			Block block = new Block(repository, previousBlockData, generator, null, null);
			block.addTransaction(voteOnPollTransactionData);
			block.sign();

			assertTrue("Block signatures invalid", block.isSignatureValid());
			assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

			block.process();
			repository.saveChanges();

			// Check vote was registered properly
			VoteOnPollData actualVoteOnPollData = repository.getVotingRepository().getVote(pollName, sender.getPublicKey());
			assertNotNull(actualVoteOnPollData);
			assertEquals(optionIndex, actualVoteOnPollData.getOptionIndex());

			// update variables for next round
			previousBlockData = block.getBlockData();
			timestamp += 1_000;
			reference = voteOnPollTransaction.getTransactionData().getSignature();
		}

		// Check poll's votes
		List<VoteOnPollData> votes = repository.getVotingRepository().getVotes(pollName);
		assertNotNull(votes);

		assertEquals("Only one vote expected", 1, votes.size());

		assertEquals("Wrong vote option index", pollOptionsSize - 1, votes.get(0).getOptionIndex());
		assertTrue("Wrong voter public key", Arrays.equals(sender.getPublicKey(), votes.get(0).getVoterPublicKey()));

		// Orphan last block
		BlockData lastBlockData = repository.getBlockRepository().getLastBlock();
		Block lastBlock = new Block(repository, lastBlockData);
		lastBlock.orphan();
		repository.saveChanges();

		// Recheck poll's votes
		votes = repository.getVotingRepository().getVotes(pollName);
		assertNotNull(votes);

		assertEquals("Only one vote expected", 1, votes.size());

		assertEquals("Wrong vote option index", pollOptionsSize - 1 - 1, votes.get(0).getOptionIndex());
		assertTrue("Wrong voter public key", Arrays.equals(sender.getPublicKey(), votes.get(0).getVoterPublicKey()));
	}

}