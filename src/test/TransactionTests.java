package test;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Test;

import com.google.common.hash.HashCode;

import data.PaymentData;
import data.account.AccountBalanceData;
import data.account.AccountData;
import data.assets.AssetData;
import data.assets.OrderData;
import data.assets.TradeData;
import data.block.BlockData;
import data.naming.NameData;
import data.transaction.BuyNameTransactionData;
import data.transaction.CancelOrderTransactionData;
import data.transaction.CancelSellNameTransactionData;
import data.transaction.CreateOrderTransactionData;
import data.transaction.CreatePollTransactionData;
import data.transaction.IssueAssetTransactionData;
import data.transaction.MessageTransactionData;
import data.transaction.MultiPaymentTransactionData;
import data.transaction.PaymentTransactionData;
import data.transaction.RegisterNameTransactionData;
import data.transaction.SellNameTransactionData;
import data.transaction.TransferAssetTransactionData;
import data.transaction.UpdateNameTransactionData;
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
import qora.transaction.BuyNameTransaction;
import qora.transaction.CancelOrderTransaction;
import qora.transaction.CancelSellNameTransaction;
import qora.transaction.CreateOrderTransaction;
import qora.transaction.CreatePollTransaction;
import qora.transaction.IssueAssetTransaction;
import qora.transaction.MessageTransaction;
import qora.transaction.MultiPaymentTransaction;
import qora.transaction.PaymentTransaction;
import qora.transaction.RegisterNameTransaction;
import qora.transaction.SellNameTransaction;
import qora.transaction.Transaction;
import qora.transaction.Transaction.ValidationResult;
import qora.transaction.TransferAssetTransaction;
import qora.transaction.UpdateNameTransaction;
import qora.transaction.VoteOnPollTransaction;
import repository.AccountRepository;
import repository.AssetRepository;
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

	private static final BigDecimal initialGeneratorBalance = BigDecimal.valueOf(1_000_000_000L).setScale(8);
	private static final BigDecimal initialSenderBalance = BigDecimal.valueOf(1_000_000L).setScale(8);
	private static final BigDecimal genericPaymentAmount = BigDecimal.valueOf(1_000L).setScale(8);

	private Repository repository;
	private AccountRepository accountRepository;
	private BlockData parentBlockData;
	private PrivateKeyAccount sender;
	private PrivateKeyAccount generator;
	private byte[] reference;

	@SuppressWarnings("unchecked")
	public void createTestAccounts(Long genesisTimestamp) throws DataException {
		RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(connectionUrl);
		RepositoryManager.setRepositoryFactory(repositoryFactory);

		try (final Repository repository = RepositoryManager.getRepository()) {
			assertEquals("Blockchain should be empty for this test", 0, repository.getBlockRepository().getBlockchainHeight());
		}

		// [Un]set genesis timestamp as required by test
		JSONObject settingsJSON = new JSONObject();
		if (genesisTimestamp != null)
			settingsJSON.put("testnetstamp", genesisTimestamp);

		Settings.test(settingsJSON);

		// This needs to be called outside of acquiring our own repository or it will deadlock
		BlockChain.validate();

		// Grab repository for further use, including during test itself
		repository = RepositoryManager.getRepository();

		// Grab genesis block
		parentBlockData = repository.getBlockRepository().fromHeight(1);

		accountRepository = repository.getAccountRepository();

		// Create test generator account
		generator = new PrivateKeyAccount(repository, generatorSeed);
		accountRepository.save(new AccountData(generator.getAddress(), generatorSeed));
		accountRepository.save(new AccountBalanceData(generator.getAddress(), Asset.QORA, initialGeneratorBalance));

		// Create test sender account
		sender = new PrivateKeyAccount(repository, senderSeed);

		// Mock account
		reference = senderSeed;
		accountRepository.save(new AccountData(sender.getAddress(), reference));

		// Mock balance
		accountRepository.save(new AccountBalanceData(sender.getAddress(), Asset.QORA, initialSenderBalance));

		repository.saveChanges();
	}

	@After
	public void closeRepository() throws DataException {
		RepositoryManager.closeRepositoryFactory();
	}

	private Transaction createPayment(PrivateKeyAccount sender, String recipient) throws DataException {
		// Make a new payment transaction
		BigDecimal amount = genericPaymentAmount;
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		PaymentTransactionData paymentTransactionData = new PaymentTransactionData(sender.getPublicKey(), recipient, amount, fee, timestamp, reference);

		Transaction paymentTransaction = new PaymentTransaction(repository, paymentTransactionData);
		paymentTransaction.sign(sender);

		return paymentTransaction;
	}

	@Test
	public void testPaymentTransaction() throws DataException {
		createTestAccounts(null);

		// Make a new payment transaction
		Account recipient = new PublicKeyAccount(repository, recipientSeed);
		BigDecimal amount = BigDecimal.valueOf(1_000L);
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		PaymentTransactionData paymentTransactionData = new PaymentTransactionData(sender.getPublicKey(), recipient.getAddress(), amount, fee, timestamp,
				reference);

		Transaction paymentTransaction = new PaymentTransaction(repository, paymentTransactionData);
		paymentTransaction.sign(sender);
		assertTrue(paymentTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, paymentTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(paymentTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check sender's balance
		BigDecimal expectedBalance = initialSenderBalance.subtract(amount).subtract(fee);
		BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Fee should be in generator's balance
		expectedBalance = initialGeneratorBalance.add(fee);
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Amount should be in recipient's balance
		expectedBalance = amount;
		actualBalance = accountRepository.getBalance(recipient.getAddress(), Asset.QORA).getBalance();
		assertTrue("Recipient's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Check recipient's reference
		byte[] recipientsReference = recipient.getLastReference();
		assertTrue("Recipient's new reference incorrect", Arrays.equals(paymentTransaction.getTransactionData().getSignature(), recipientsReference));

		// Orphan block
		block.orphan();
		repository.saveChanges();

		// Check sender's balance
		actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's reverted balance incorrect", initialSenderBalance.compareTo(actualBalance) == 0);

		// Check generator's balance
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", initialGeneratorBalance.compareTo(actualBalance) == 0);
	}

	@Test
	public void testRegisterNameTransaction() throws DataException {
		createTestAccounts(null);

		// Make a new register name transaction
		String name = "test name";
		String data = "{\"key\":\"value\"}";

		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		RegisterNameTransactionData registerNameTransactionData = new RegisterNameTransactionData(sender.getPublicKey(), sender.getAddress(), name, data, fee,
				timestamp, reference);

		Transaction registerNameTransaction = new RegisterNameTransaction(repository, registerNameTransactionData);
		registerNameTransaction.sign(sender);
		assertTrue(registerNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, registerNameTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(registerNameTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check sender's balance
		BigDecimal expectedBalance = initialSenderBalance.subtract(fee);
		BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Fee should be in generator's balance
		expectedBalance = initialGeneratorBalance.add(fee);
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Check name was registered
		NameData actualNameData = this.repository.getNameRepository().fromName(name);
		assertNotNull(actualNameData);

		// Check sender's reference
		assertTrue("Sender's new reference incorrect", Arrays.equals(registerNameTransactionData.getSignature(), sender.getLastReference()));

		// Update variables for use by other tests
		reference = sender.getLastReference();
		parentBlockData = block.getBlockData();
	}

	@Test
	public void testUpdateNameTransaction() throws DataException {
		// Register name using another test
		testRegisterNameTransaction();

		String name = "test name";
		NameData originalNameData = this.repository.getNameRepository().fromName(name);

		// Update name's owner and data
		Account newOwner = new PublicKeyAccount(repository, recipientSeed);
		String newData = "{\"newKey\":\"newValue\"}";
		byte[] nameReference = reference;

		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		UpdateNameTransactionData updateNameTransactionData = new UpdateNameTransactionData(sender.getPublicKey(), newOwner.getAddress(), name, newData,
				nameReference, fee, timestamp, reference);

		Transaction updateNameTransaction = new UpdateNameTransaction(repository, updateNameTransactionData);
		updateNameTransaction.sign(sender);
		assertTrue(updateNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, updateNameTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(updateNameTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check name was updated
		NameData actualNameData = this.repository.getNameRepository().fromName(name);
		assertEquals(newOwner.getAddress(), actualNameData.getOwner());
		assertEquals(newData, actualNameData.getData());

		// Now orphan block
		block.orphan();
		repository.saveChanges();

		// Check name has been reverted correctly
		actualNameData = this.repository.getNameRepository().fromName(name);
		assertEquals(originalNameData.getOwner(), actualNameData.getOwner());
		assertEquals(originalNameData.getData(), actualNameData.getData());
	}

	@Test
	public void testSellNameTransaction() throws DataException {
		// Register name using another test
		testRegisterNameTransaction();

		String name = "test name";

		// Sale price
		BigDecimal amount = BigDecimal.valueOf(1234L).setScale(8);

		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		SellNameTransactionData sellNameTransactionData = new SellNameTransactionData(sender.getPublicKey(), name, amount, fee, timestamp, reference);

		Transaction sellNameTransaction = new SellNameTransaction(repository, sellNameTransactionData);
		sellNameTransaction.sign(sender);
		assertTrue(sellNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, sellNameTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(sellNameTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check name was updated
		NameData actualNameData = this.repository.getNameRepository().fromName(name);
		assertTrue(actualNameData.getIsForSale());
		assertEquals(amount, actualNameData.getSalePrice());

		// Now orphan block
		block.orphan();
		repository.saveChanges();

		// Check name has been reverted correctly
		actualNameData = this.repository.getNameRepository().fromName(name);
		assertFalse(actualNameData.getIsForSale());
		assertNull(actualNameData.getSalePrice());

		// Re-process block for use by other tests
		block.process();
		repository.saveChanges();

		// Update variables for use by other tests
		reference = sender.getLastReference();
		parentBlockData = block.getBlockData();
	}

	@Test
	public void testCancelSellNameTransaction() throws DataException {
		// Register and sell name using another test
		testSellNameTransaction();

		String name = "test name";
		NameData originalNameData = this.repository.getNameRepository().fromName(name);

		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		CancelSellNameTransactionData cancelSellNameTransactionData = new CancelSellNameTransactionData(sender.getPublicKey(), name, fee, timestamp, reference);

		Transaction cancelSellNameTransaction = new CancelSellNameTransaction(repository, cancelSellNameTransactionData);
		cancelSellNameTransaction.sign(sender);
		assertTrue(cancelSellNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, cancelSellNameTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(cancelSellNameTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check name was updated
		NameData actualNameData = this.repository.getNameRepository().fromName(name);
		assertFalse(actualNameData.getIsForSale());
		assertEquals(originalNameData.getSalePrice(), actualNameData.getSalePrice());

		// Now orphan block
		block.orphan();
		repository.saveChanges();

		// Check name has been reverted correctly
		actualNameData = this.repository.getNameRepository().fromName(name);
		assertTrue(actualNameData.getIsForSale());
		assertEquals(originalNameData.getSalePrice(), actualNameData.getSalePrice());

		// Update variables for use by other tests
		reference = sender.getLastReference();
		parentBlockData = block.getBlockData();
	}

	@Test
	public void testBuyNameTransaction() throws DataException {
		// Register and sell name using another test
		testSellNameTransaction();

		String name = "test name";
		NameData originalNameData = this.repository.getNameRepository().fromName(name);
		String seller = originalNameData.getOwner();

		// Buyer
		PrivateKeyAccount buyer = new PrivateKeyAccount(repository, recipientSeed);
		byte[] nameReference = reference;

		// Send buyer some funds so they have a reference
		Transaction somePaymentTransaction = createPayment(sender, buyer.getAddress());
		byte[] buyersReference = somePaymentTransaction.getTransactionData().getSignature();

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(somePaymentTransaction.getTransactionData());
		block.sign();

		block.process();
		repository.saveChanges();
		parentBlockData = block.getBlockData();

		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		BuyNameTransactionData buyNameTransactionData = new BuyNameTransactionData(buyer.getPublicKey(), name, originalNameData.getSalePrice(), seller,
				nameReference, fee, timestamp, buyersReference);

		Transaction buyNameTransaction = new BuyNameTransaction(repository, buyNameTransactionData);
		buyNameTransaction.sign(buyer);
		assertTrue(buyNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, buyNameTransaction.isValid());

		// Forge new block with transaction
		block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(buyNameTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check name was updated
		NameData actualNameData = this.repository.getNameRepository().fromName(name);
		assertFalse(actualNameData.getIsForSale());
		assertEquals(originalNameData.getSalePrice(), actualNameData.getSalePrice());
		assertEquals(buyer.getAddress(), actualNameData.getOwner());

		// Now orphan block
		block.orphan();
		repository.saveChanges();

		// Check name has been reverted correctly
		actualNameData = this.repository.getNameRepository().fromName(name);
		assertTrue(actualNameData.getIsForSale());
		assertEquals(originalNameData.getSalePrice(), actualNameData.getSalePrice());
		assertEquals(originalNameData.getOwner(), actualNameData.getOwner());
	}

	@Test
	public void testCreatePollTransaction() throws DataException {
		// This test requires GenesisBlock's timestamp is set to something after BlockChain.VOTING_RELEASE_TIMESTAMP
		createTestAccounts(BlockChain.getVotingReleaseTimestamp() + 1_000L);

		// Make a new create poll transaction
		String pollName = "test poll";
		String description = "test poll description";

		List<PollOptionData> pollOptions = new ArrayList<PollOptionData>();
		pollOptions.add(new PollOptionData("abort"));
		pollOptions.add(new PollOptionData("retry"));
		pollOptions.add(new PollOptionData("fail"));

		Account recipient = new PublicKeyAccount(repository, recipientSeed);
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		CreatePollTransactionData createPollTransactionData = new CreatePollTransactionData(sender.getPublicKey(), recipient.getAddress(), pollName,
				description, pollOptions, fee, timestamp, reference);

		Transaction createPollTransaction = new CreatePollTransaction(repository, createPollTransactionData);
		createPollTransaction.sign(sender);
		assertTrue(createPollTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, createPollTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(createPollTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check sender's balance
		BigDecimal expectedBalance = initialSenderBalance.subtract(fee);
		BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Fee should be in generator's balance
		expectedBalance = initialGeneratorBalance.add(fee);
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Check poll was created
		PollData actualPollData = this.repository.getVotingRepository().fromPollName(pollName);
		assertNotNull(actualPollData);

		// Check sender's reference
		assertTrue("Sender's new reference incorrect", Arrays.equals(createPollTransactionData.getSignature(), sender.getLastReference()));

		// Update variables for use by other tests
		reference = sender.getLastReference();
		parentBlockData = block.getBlockData();
	}

	@Test
	public void testVoteOnPollTransaction() throws DataException {
		// Create poll using another test
		testCreatePollTransaction();

		// Try all options, plus invalid optionIndex (note use of <= for this)
		String pollName = "test poll";
		int pollOptionsSize = 3;
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;

		for (int optionIndex = 0; optionIndex <= pollOptionsSize; ++optionIndex) {
			// Make a vote-on-poll transaction
			VoteOnPollTransactionData voteOnPollTransactionData = new VoteOnPollTransactionData(sender.getPublicKey(), pollName, optionIndex, fee, timestamp,
					reference);

			Transaction voteOnPollTransaction = new VoteOnPollTransaction(repository, voteOnPollTransactionData);
			voteOnPollTransaction.sign(sender);
			assertTrue(voteOnPollTransaction.isSignatureValid());

			if (optionIndex == pollOptionsSize) {
				assertEquals(ValidationResult.POLL_OPTION_DOES_NOT_EXIST, voteOnPollTransaction.isValid());
				break;
			}
			assertEquals(ValidationResult.OK, voteOnPollTransaction.isValid());

			// Forge new block with transaction
			Block block = new Block(repository, parentBlockData, generator, null, null);
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
			parentBlockData = block.getBlockData();
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

		// Re-check poll's votes
		votes = repository.getVotingRepository().getVotes(pollName);
		assertNotNull(votes);

		assertEquals("Only one vote expected", 1, votes.size());

		assertEquals("Wrong vote option index", pollOptionsSize - 1 - 1, votes.get(0).getOptionIndex());
		assertTrue("Wrong voter public key", Arrays.equals(sender.getPublicKey(), votes.get(0).getVoterPublicKey()));
	}

	@Test
	public void testIssueAssetTransaction() throws DataException {
		createTestAccounts(null);

		// Create new asset
		String assetName = "test asset";
		String description = "test asset description";
		long quantity = 1_000_000L;
		boolean isDivisible = false;
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;

		IssueAssetTransactionData issueAssetTransactionData = new IssueAssetTransactionData(sender.getPublicKey(), sender.getAddress(), assetName, description,
				quantity, isDivisible, fee, timestamp, reference);

		Transaction issueAssetTransaction = new IssueAssetTransaction(repository, issueAssetTransactionData);
		issueAssetTransaction.sign(sender);
		assertTrue(issueAssetTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, issueAssetTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(issueAssetTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check sender's balance
		BigDecimal expectedBalance = initialSenderBalance.subtract(fee);
		BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Fee should be in generator's balance
		expectedBalance = initialGeneratorBalance.add(fee);
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Check we now have an assetId
		Long assetId = issueAssetTransactionData.getAssetId();
		assertNotNull(assetId);
		// Should NOT collide with Asset.QORA
		assertFalse(assetId == Asset.QORA);

		// Check asset now exists
		AssetRepository assetRepo = this.repository.getAssetRepository();
		assertTrue(assetRepo.assetExists(assetId));
		assertTrue(assetRepo.assetExists(assetName));
		// Check asset data
		AssetData assetData = assetRepo.fromAssetId(assetId);
		assertNotNull(assetData);
		assertEquals(assetName, assetData.getName());
		assertEquals(description, assetData.getDescription());

		// Orphan block
		block.orphan();
		repository.saveChanges();

		// Check sender's balance
		actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's reverted balance incorrect", initialSenderBalance.compareTo(actualBalance) == 0);

		// Check generator's balance
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's reverted balance incorrect", initialGeneratorBalance.compareTo(actualBalance) == 0);

		// Check asset no longer exists
		assertFalse(assetRepo.assetExists(assetId));
		assertFalse(assetRepo.assetExists(assetName));
		assetData = assetRepo.fromAssetId(assetId);
		assertNull(assetData);

		// Re-process block for use by other tests
		block.process();
		repository.saveChanges();

		// Update variables for use by other tests
		reference = sender.getLastReference();
		parentBlockData = block.getBlockData();
	}

	@Test
	public void testTransferAssetTransaction() throws DataException {
		// Issue asset using another test
		testIssueAssetTransaction();

		String assetName = "test asset";
		AssetRepository assetRepo = this.repository.getAssetRepository();
		AssetData originalAssetData = assetRepo.fromAssetName(assetName);
		long assetId = originalAssetData.getAssetId();
		BigDecimal originalSenderBalance = sender.getConfirmedBalance(Asset.QORA);
		BigDecimal originalGeneratorBalance = generator.getConfirmedBalance(Asset.QORA);

		// Transfer asset to new recipient
		Account recipient = new PublicKeyAccount(repository, recipientSeed);
		BigDecimal amount = BigDecimal.valueOf(1_000L).setScale(8);
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;

		TransferAssetTransactionData transferAssetTransactionData = new TransferAssetTransactionData(sender.getPublicKey(), recipient.getAddress(), amount,
				assetId, fee, timestamp, reference);

		Transaction transferAssetTransaction = new TransferAssetTransaction(repository, transferAssetTransactionData);
		transferAssetTransaction.sign(sender);
		assertTrue(transferAssetTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, transferAssetTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(transferAssetTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check sender's balance
		BigDecimal expectedBalance = originalSenderBalance.subtract(fee);
		BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Fee should be in generator's balance
		expectedBalance = originalGeneratorBalance.add(fee);
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Check asset balances
		BigDecimal actualSenderAssetBalance = sender.getConfirmedBalance(assetId);
		assertNotNull(actualSenderAssetBalance);
		BigDecimal expectedSenderAssetBalance = BigDecimal.valueOf(originalAssetData.getQuantity()).setScale(8).subtract(amount);
		assertEquals(expectedSenderAssetBalance, actualSenderAssetBalance);

		BigDecimal actualRecipientAssetBalance = recipient.getConfirmedBalance(assetId);
		assertNotNull(actualRecipientAssetBalance);
		assertEquals(amount, actualRecipientAssetBalance);

		// Orphan block
		block.orphan();
		repository.saveChanges();

		// Check sender's balance
		actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's reverted balance incorrect", originalSenderBalance.compareTo(actualBalance) == 0);

		// Check generator's balance
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's reverted balance incorrect", originalGeneratorBalance.compareTo(actualBalance) == 0);

		// Check asset balances
		actualSenderAssetBalance = sender.getConfirmedBalance(assetId);
		assertNotNull(actualSenderAssetBalance);
		expectedSenderAssetBalance = BigDecimal.valueOf(originalAssetData.getQuantity()).setScale(8);
		assertEquals(expectedSenderAssetBalance, actualSenderAssetBalance);

		actualRecipientAssetBalance = recipient.getConfirmedBalance(assetId);
		if (actualRecipientAssetBalance != null)
			assertEquals(BigDecimal.ZERO.setScale(8), actualRecipientAssetBalance);

		// Re-process block for use by other tests
		block.process();
		repository.saveChanges();

		// Update variables for use by other tests
		reference = sender.getLastReference();
		parentBlockData = block.getBlockData();
	}

	@Test
	public void testCreateAssetOrderTransaction() throws DataException {
		// Issue asset using another test
		testIssueAssetTransaction();

		// Asset info
		String assetName = "test asset";
		AssetRepository assetRepo = this.repository.getAssetRepository();
		AssetData originalAssetData = assetRepo.fromAssetName(assetName);
		long assetId = originalAssetData.getAssetId();

		// Buyer
		PrivateKeyAccount buyer = new PrivateKeyAccount(repository, recipientSeed);

		// Send buyer some funds so they have a reference
		Transaction somePaymentTransaction = createPayment(sender, buyer.getAddress());
		byte[] buyersReference = somePaymentTransaction.getTransactionData().getSignature();

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(somePaymentTransaction.getTransactionData());
		block.sign();

		block.process();
		repository.saveChanges();
		parentBlockData = block.getBlockData();

		// Order: buyer has 10 QORA and wants to buy "test asset" at a price of 50 "test asset" per QORA.
		long haveAssetId = Asset.QORA;
		BigDecimal amount = BigDecimal.valueOf(10).setScale(8);
		long wantAssetId = assetId;
		BigDecimal price = BigDecimal.valueOf(50).setScale(8);
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;

		CreateOrderTransactionData createOrderTransactionData = new CreateOrderTransactionData(buyer.getPublicKey(), haveAssetId, wantAssetId, amount, price,
				fee, timestamp, buyersReference);
		Transaction createOrderTransaction = new CreateOrderTransaction(this.repository, createOrderTransactionData);
		createOrderTransaction.sign(buyer);
		assertTrue(createOrderTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, createOrderTransaction.isValid());

		// Forge new block with transaction
		block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(createOrderTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check order was created
		byte[] orderId = createOrderTransactionData.getSignature();
		OrderData orderData = assetRepo.fromOrderId(orderId);
		assertNotNull(orderData);

		// Check buyer's balance reduced
		BigDecimal expectedBalance = genericPaymentAmount.subtract(amount).subtract(fee);
		BigDecimal actualBalance = buyer.getConfirmedBalance(haveAssetId);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);

		// Orphan transaction
		block.orphan();
		repository.saveChanges();

		// Check order no longer exists
		orderData = assetRepo.fromOrderId(orderId);
		assertNull(orderData);

		// Check buyer's balance restored
		expectedBalance = genericPaymentAmount;
		actualBalance = buyer.getConfirmedBalance(haveAssetId);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);

		// Re-process to allow use by other tests
		block.process();
		repository.saveChanges();

		// Update variables for use by other tests
		reference = sender.getLastReference();
		parentBlockData = block.getBlockData();
	}

	@Test
	public void testCancelAssetOrderTransaction() throws DataException {
		// Issue asset and create order using another test
		testCreateAssetOrderTransaction();

		// Asset info
		String assetName = "test asset";
		AssetRepository assetRepo = this.repository.getAssetRepository();
		AssetData originalAssetData = assetRepo.fromAssetName(assetName);
		long assetId = originalAssetData.getAssetId();

		// Buyer
		PrivateKeyAccount buyer = new PrivateKeyAccount(repository, recipientSeed);

		// Fetch orders
		long haveAssetId = Asset.QORA;
		long wantAssetId = assetId;
		List<OrderData> orders = assetRepo.getOpenOrders(haveAssetId, wantAssetId);

		assertNotNull(orders);
		assertEquals(1, orders.size());

		OrderData originalOrderData = orders.get(0);
		assertNotNull(originalOrderData);
		assertFalse(originalOrderData.getIsClosed());

		// Create cancel order transaction
		byte[] orderId = originalOrderData.getOrderId();
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		byte[] buyersReference = buyer.getLastReference();
		CancelOrderTransactionData cancelOrderTransactionData = new CancelOrderTransactionData(buyer.getPublicKey(), orderId, fee, timestamp, buyersReference);

		Transaction cancelOrderTransaction = new CancelOrderTransaction(this.repository, cancelOrderTransactionData);
		cancelOrderTransaction.sign(buyer);
		assertTrue(cancelOrderTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, cancelOrderTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(cancelOrderTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check order is marked as cancelled
		OrderData cancelledOrderData = assetRepo.fromOrderId(orderId);
		assertNotNull(cancelledOrderData);
		assertTrue(cancelledOrderData.getIsClosed());

		// Orphan
		block.orphan();
		repository.saveChanges();

		// Check order is no longer marked as cancelled
		OrderData uncancelledOrderData = assetRepo.fromOrderId(orderId);
		assertNotNull(uncancelledOrderData);
		assertFalse(uncancelledOrderData.getIsClosed());

	}

	@Test
	public void testMatchingCreateAssetOrderTransaction() throws DataException {
		// Issue asset and create order using another test
		testCreateAssetOrderTransaction();

		// Asset info
		String assetName = "test asset";
		AssetRepository assetRepo = this.repository.getAssetRepository();
		AssetData originalAssetData = assetRepo.fromAssetName(assetName);
		long assetId = originalAssetData.getAssetId();

		// Buyer
		PrivateKeyAccount buyer = new PrivateKeyAccount(repository, recipientSeed);

		// Fetch orders
		long originalHaveAssetId = Asset.QORA;
		long originalWantAssetId = assetId;
		List<OrderData> orders = assetRepo.getOpenOrders(originalHaveAssetId, originalWantAssetId);

		assertNotNull(orders);
		assertEquals(1, orders.size());

		OrderData originalOrderData = orders.get(0);
		assertNotNull(originalOrderData);
		assertFalse(originalOrderData.getIsClosed());

		// Original asset owner (sender) will sell asset to "buyer"

		// Order: seller has 40 "test asset" and wants to buy QORA at a price of 1/60 QORA per "test asset".
		// This order should be a partial match for original order, and at a better price than asked
		long haveAssetId = Asset.QORA;
		BigDecimal amount = BigDecimal.valueOf(40).setScale(8);
		long wantAssetId = assetId;
		BigDecimal price = BigDecimal.ONE.setScale(8).divide(BigDecimal.valueOf(60).setScale(8));
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;

		CreateOrderTransactionData createOrderTransactionData = new CreateOrderTransactionData(sender.getPublicKey(), haveAssetId, wantAssetId, amount, price,
				fee, timestamp, reference);
		Transaction createOrderTransaction = new CreateOrderTransaction(this.repository, createOrderTransactionData);
		createOrderTransaction.sign(sender);
		assertTrue(createOrderTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, createOrderTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(createOrderTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check order was created
		byte[] orderId = createOrderTransactionData.getSignature();
		OrderData orderData = assetRepo.fromOrderId(orderId);
		assertNotNull(orderData);
		assertFalse(orderData.getIsFulfilled());

		// Check order has trades
		List<TradeData> trades = assetRepo.getOrdersTrades(orderId);
		assertNotNull(trades);
		assertEquals(1, trades.size());
		TradeData tradeData = trades.get(0);

		// Check trade has correct values
		BigDecimal expectedAmount = amount.multiply(price);
		BigDecimal actualAmount = tradeData.getAmount();
		assertTrue(expectedAmount.compareTo(actualAmount) == 0);

		BigDecimal expectedPrice = originalOrderData.getPrice().multiply(amount);
		BigDecimal actualPrice = tradeData.getPrice();
		assertTrue(expectedPrice.compareTo(actualPrice) == 0);

		// Check seller's "test asset" balance
		BigDecimal expectedBalance = BigDecimal.valueOf(1_000_000L).setScale(8).subtract(amount);
		BigDecimal actualBalance = sender.getConfirmedBalance(haveAssetId);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);

		// Check buyer's "test asset" balance
		expectedBalance = amount;
		actualBalance = buyer.getConfirmedBalance(haveAssetId);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);

		// Check seller's QORA balance
		expectedBalance = initialSenderBalance.subtract(BigDecimal.ONE).subtract(BigDecimal.ONE);
		actualBalance = sender.getConfirmedBalance(wantAssetId);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);

		// Orphan transaction
		block.orphan();
		repository.saveChanges();

		// Check order no longer exists
		orderData = assetRepo.fromOrderId(orderId);
		assertNull(orderData);

		// Check trades no longer exist
		trades = assetRepo.getOrdersTrades(orderId);
		assertNotNull(trades);
		assertEquals(0, trades.size());

		// Check seller's "test asset" balance restored
		expectedBalance = BigDecimal.valueOf(1_000_000L).setScale(8);
		actualBalance = sender.getConfirmedBalance(haveAssetId);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);

		// Check buyer's "test asset" balance restored
		expectedBalance = BigDecimal.ZERO.setScale(8);
		actualBalance = buyer.getConfirmedBalance(haveAssetId);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);
	}

	@Test
	public void testMultiPaymentTransaction() throws DataException {
		createTestAccounts(null);

		// Make a new multi-payment transaction
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;

		// Payments
		BigDecimal expectedSenderBalance = initialSenderBalance.subtract(fee);
		List<PaymentData> payments = new ArrayList<PaymentData>();
		for (int i = 0; i < 5; ++i) {
			byte[] seed = recipientSeed.clone();
			seed[0] += i;
			Account recipient = new PublicKeyAccount(repository, seed);
			long assetId = Asset.QORA;

			BigDecimal amount = BigDecimal.valueOf(1_000L + i).setScale(8);
			expectedSenderBalance = expectedSenderBalance.subtract(amount);

			PaymentData paymentData = new PaymentData(recipient.getAddress(), assetId, amount);

			payments.add(paymentData);
		}

		MultiPaymentTransactionData multiPaymentTransactionData = new MultiPaymentTransactionData(sender.getPublicKey(), payments, fee, timestamp, reference);

		Transaction multiPaymentTransaction = new MultiPaymentTransaction(repository, multiPaymentTransactionData);
		multiPaymentTransaction.sign(sender);
		assertTrue(multiPaymentTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, multiPaymentTransaction.isValid());

		// Forge new block with payment transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(multiPaymentTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check sender's balance
		BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's new balance incorrect", expectedSenderBalance.compareTo(actualBalance) == 0);

		// Fee should be in generator's balance
		BigDecimal expectedBalance = initialGeneratorBalance.add(fee);
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Check recipients
		for (int i = 0; i < payments.size(); ++i) {
			PaymentData paymentData = payments.get(i);
			Account recipient = new Account(this.repository, paymentData.getRecipient());

			byte[] recipientsReference = recipient.getLastReference();
			assertTrue("Recipient's new reference incorrect", Arrays.equals(multiPaymentTransaction.getTransactionData().getSignature(), recipientsReference));

			// Amount should be in recipient's balance
			expectedBalance = paymentData.getAmount();
			actualBalance = accountRepository.getBalance(recipient.getAddress(), Asset.QORA).getBalance();
			assertTrue("Recipient's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		}

		// Orphan block
		block.orphan();
		repository.saveChanges();

		// Check sender's balance
		actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's reverted balance incorrect", initialSenderBalance.compareTo(actualBalance) == 0);

		// Check generator's balance
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", initialGeneratorBalance.compareTo(actualBalance) == 0);
	}

	@Test
	public void testMessageTransaction() throws DataException, UnsupportedEncodingException {
		createTestAccounts(1431861220336L); // timestamp taken from main blockchain block 99000

		// Make a new message transaction
		Account recipient = new PublicKeyAccount(repository, recipientSeed);
		BigDecimal amount = BigDecimal.valueOf(1_000L);
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		int version = Transaction.getVersionByTimestamp(timestamp);
		byte[] data = "test".getBytes("UTF-8");
		boolean isText = true;
		boolean isEncrypted = false;

		MessageTransactionData messageTransactionData = new MessageTransactionData(version, sender.getPublicKey(), recipient.getAddress(), Asset.QORA, amount,
				data, isText, isEncrypted, fee, timestamp, reference);

		Transaction messageTransaction = new MessageTransaction(repository, messageTransactionData);
		messageTransaction.sign(sender);
		assertTrue(messageTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, messageTransaction.isValid());

		// Forge new block with message transaction
		Block block = new Block(repository, parentBlockData, generator, null, null);
		block.addTransaction(messageTransactionData);
		block.sign();

		assertTrue("Block signatures invalid", block.isSignatureValid());
		assertEquals("Block is invalid", Block.ValidationResult.OK, block.isValid());

		block.process();
		repository.saveChanges();

		// Check sender's balance
		BigDecimal expectedBalance = initialSenderBalance.subtract(amount).subtract(fee);
		BigDecimal actualBalance = accountRepository.getBalance(sender.getAddress(), Asset.QORA).getBalance();
		assertTrue("Sender's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Fee should be in generator's balance
		expectedBalance = initialGeneratorBalance.add(fee);
		actualBalance = accountRepository.getBalance(generator.getAddress(), Asset.QORA).getBalance();
		assertTrue("Generator's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);

		// Amount should be in recipient's balance
		expectedBalance = amount;
		actualBalance = accountRepository.getBalance(recipient.getAddress(), Asset.QORA).getBalance();
		assertTrue("Recipient's new balance incorrect", expectedBalance.compareTo(actualBalance) == 0);
	}

}