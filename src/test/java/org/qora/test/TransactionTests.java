package org.qora.test;

import org.junit.After;
import org.junit.Test;
import org.qora.account.Account;
import org.qora.account.PrivateKeyAccount;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.Block;
import org.qora.block.BlockChain;
import org.qora.data.PaymentData;
import org.qora.data.account.AccountBalanceData;
import org.qora.data.account.AccountData;
import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.data.asset.TradeData;
import org.qora.data.block.BlockData;
import org.qora.data.naming.NameData;
import org.qora.data.transaction.BuyNameTransactionData;
import org.qora.data.transaction.CancelAssetOrderTransactionData;
import org.qora.data.transaction.CancelSellNameTransactionData;
import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.CreatePollTransactionData;
import org.qora.data.transaction.IssueAssetTransactionData;
import org.qora.data.transaction.MessageTransactionData;
import org.qora.data.transaction.MultiPaymentTransactionData;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.RegisterNameTransactionData;
import org.qora.data.transaction.SellNameTransactionData;
import org.qora.data.transaction.TransferAssetTransactionData;
import org.qora.data.transaction.UpdateNameTransactionData;
import org.qora.data.transaction.VoteOnPollTransactionData;
import org.qora.data.voting.PollData;
import org.qora.data.voting.PollOptionData;
import org.qora.data.voting.VoteOnPollData;
import org.qora.group.Group;
import org.qora.repository.AccountRepository;
import org.qora.repository.AssetRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.transaction.BuyNameTransaction;
import org.qora.transaction.CancelAssetOrderTransaction;
import org.qora.transaction.CancelSellNameTransaction;
import org.qora.transaction.CreateAssetOrderTransaction;
import org.qora.transaction.CreatePollTransaction;
import org.qora.transaction.IssueAssetTransaction;
import org.qora.transaction.MessageTransaction;
import org.qora.transaction.MultiPaymentTransaction;
import org.qora.transaction.PaymentTransaction;
import org.qora.transaction.RegisterNameTransaction;
import org.qora.transaction.SellNameTransaction;
import org.qora.transaction.Transaction;
import org.qora.transaction.TransferAssetTransaction;
import org.qora.transaction.UpdateNameTransaction;
import org.qora.transaction.VoteOnPollTransaction;
import org.qora.transaction.Transaction.ValidationResult;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.hash.HashCode;

public class TransactionTests extends Common {

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

	public void createTestAccounts(Long genesisTimestamp) throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			assertEquals("Blockchain should be empty for this test", 0, repository.getBlockRepository().getBlockchainHeight());
		}

		// This needs to be called outside of acquiring our own repository or it will deadlock
		BlockChain.validate();

		// Grab repository for further use, including during test itself
		repository = RepositoryManager.getRepository();

		// Grab genesis block
		parentBlockData = repository.getBlockRepository().fromHeight(1);

		accountRepository = repository.getAccountRepository();

		// Create test generator account
		generator = new PrivateKeyAccount(repository, generatorSeed);
		accountRepository.setLastReference(new AccountData(generator.getAddress(), generatorSeed, generator.getPublicKey(), Group.NO_GROUP, 0, null));
		accountRepository.save(new AccountBalanceData(generator.getAddress(), Asset.QORA, initialGeneratorBalance));

		// Create test sender account
		sender = new PrivateKeyAccount(repository, senderSeed);

		// Mock account
		reference = senderSeed;
		accountRepository.setLastReference(new AccountData(sender.getAddress(), reference, sender.getPublicKey(), Group.NO_GROUP, 0, null));

		// Mock balance
		accountRepository.save(new AccountBalanceData(sender.getAddress(), Asset.QORA, initialSenderBalance));

		repository.saveChanges();
	}

	@After
	public void afterTest() throws DataException {
		repository.close();
	}

	private Transaction createPayment(PrivateKeyAccount sender, String recipient) throws DataException {
		// Make a new payment transaction
		BigDecimal amount = genericPaymentAmount;
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		PaymentTransactionData paymentTransactionData = new PaymentTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(), recipient,
				amount, fee);

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
		PaymentTransactionData paymentTransactionData = new PaymentTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(),
				recipient.getAddress(), amount, fee);

		Transaction paymentTransaction = new PaymentTransaction(repository, paymentTransactionData);
		paymentTransaction.sign(sender);
		assertTrue(paymentTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, paymentTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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
		RegisterNameTransactionData registerNameTransactionData = new RegisterNameTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(),
				sender.getAddress(), name, data, fee);

		Transaction registerNameTransaction = new RegisterNameTransaction(repository, registerNameTransactionData);
		registerNameTransaction.sign(sender);
		assertTrue(registerNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, registerNameTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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
		UpdateNameTransactionData updateNameTransactionData = new UpdateNameTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(),
				newOwner.getAddress(), name, newData, nameReference, fee);

		Transaction updateNameTransaction = new UpdateNameTransaction(repository, updateNameTransactionData);
		updateNameTransaction.sign(sender);
		assertTrue(updateNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, updateNameTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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
		SellNameTransactionData sellNameTransactionData = new SellNameTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(), name, amount,
				fee);

		Transaction sellNameTransaction = new SellNameTransaction(repository, sellNameTransactionData);
		sellNameTransaction.sign(sender);
		assertTrue(sellNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, sellNameTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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
		CancelSellNameTransactionData cancelSellNameTransactionData = new CancelSellNameTransactionData(timestamp, Group.NO_GROUP, reference,
				sender.getPublicKey(), name, fee);

		Transaction cancelSellNameTransaction = new CancelSellNameTransaction(repository, cancelSellNameTransactionData);
		cancelSellNameTransaction.sign(sender);
		assertTrue(cancelSellNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, cancelSellNameTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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
		Block block = new Block(repository, parentBlockData, generator);
		block.addTransaction(somePaymentTransaction.getTransactionData());
		block.sign();

		block.process();
		repository.saveChanges();
		parentBlockData = block.getBlockData();

		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		BuyNameTransactionData buyNameTransactionData = new BuyNameTransactionData(timestamp, Group.NO_GROUP, buyersReference, buyer.getPublicKey(), name,
				originalNameData.getSalePrice(), seller, nameReference, fee);

		Transaction buyNameTransaction = new BuyNameTransaction(repository, buyNameTransactionData);
		buyNameTransaction.sign(buyer);
		assertTrue(buyNameTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, buyNameTransaction.isValid());

		// Forge new block with transaction
		block = new Block(repository, parentBlockData, generator);
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
		createTestAccounts(BlockChain.getInstance().getVotingReleaseTimestamp() + 1_000L);

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
		CreatePollTransactionData createPollTransactionData = new CreatePollTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(),
				recipient.getAddress(), pollName, description, pollOptions, fee);

		Transaction createPollTransaction = new CreatePollTransaction(repository, createPollTransactionData);
		createPollTransaction.sign(sender);
		assertTrue(createPollTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, createPollTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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
			VoteOnPollTransactionData voteOnPollTransactionData = new VoteOnPollTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(),
					pollName, optionIndex, fee);

			Transaction voteOnPollTransaction = new VoteOnPollTransaction(repository, voteOnPollTransactionData);
			voteOnPollTransaction.sign(sender);
			assertTrue(voteOnPollTransaction.isSignatureValid());

			if (optionIndex == pollOptionsSize) {
				assertEquals(ValidationResult.POLL_OPTION_DOES_NOT_EXIST, voteOnPollTransaction.isValid());
				break;
			}
			assertEquals(ValidationResult.OK, voteOnPollTransaction.isValid());

			// Forge new block with transaction
			Block block = new Block(repository, parentBlockData, generator);
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
		boolean isDivisible = true;
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		String data = (timestamp >= BlockChain.getInstance().getQoraV2Timestamp()) ? "{}" : null;

		IssueAssetTransactionData issueAssetTransactionData = new IssueAssetTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(),
				sender.getAddress(), assetName, description, quantity, isDivisible, data, fee);

		Transaction issueAssetTransaction = new IssueAssetTransaction(repository, issueAssetTransactionData);
		issueAssetTransaction.sign(sender);
		assertTrue(issueAssetTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, issueAssetTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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

		TransferAssetTransactionData transferAssetTransactionData = new TransferAssetTransactionData(timestamp, Group.NO_GROUP, reference,
				sender.getPublicKey(), recipient.getAddress(), amount, assetId, fee);

		Transaction transferAssetTransaction = new TransferAssetTransaction(repository, transferAssetTransactionData);
		transferAssetTransaction.sign(sender);
		assertTrue(transferAssetTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, transferAssetTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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
		Block block = new Block(repository, parentBlockData, generator);
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

		CreateAssetOrderTransactionData createOrderTransactionData = new CreateAssetOrderTransactionData(timestamp, Group.NO_GROUP, buyersReference,
				buyer.getPublicKey(), haveAssetId, wantAssetId, amount, price, fee);
		Transaction createOrderTransaction = new CreateAssetOrderTransaction(this.repository, createOrderTransactionData);
		createOrderTransaction.sign(buyer);
		assertTrue(createOrderTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, createOrderTransaction.isValid());

		// Forge new block with transaction
		block = new Block(repository, parentBlockData, generator);
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
		CancelAssetOrderTransactionData cancelOrderTransactionData = new CancelAssetOrderTransactionData(timestamp, Group.NO_GROUP, buyersReference,
				buyer.getPublicKey(), orderId, fee);

		Transaction cancelOrderTransaction = new CancelAssetOrderTransaction(this.repository, cancelOrderTransactionData);
		cancelOrderTransaction.sign(buyer);
		assertTrue(cancelOrderTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, cancelOrderTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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

		// Unfulfilled order: "buyer" has 10 QORA and wants to buy "test asset" at a price of 50 "test asset" per QORA.
		// buyer's order: have=QORA, amount=10, want=test-asset, price=50 (test-asset per QORA, so max return is 500 test-asset)

		// Original asset owner (sender) will sell asset to "buyer"

		// Order: seller has 40 "test asset" and wants to buy QORA at a price of 1/60 QORA per "test asset".
		// This order should be a partial match for original order, and at a better price than asked
		long haveAssetId = assetId;
		BigDecimal amount = BigDecimal.valueOf(40).setScale(8);
		long wantAssetId = Asset.QORA;
		BigDecimal price = BigDecimal.ONE.setScale(8).divide(BigDecimal.valueOf(60).setScale(8), RoundingMode.DOWN);
		BigDecimal fee = BigDecimal.ONE;
		long timestamp = parentBlockData.getTimestamp() + 1_000;
		BigDecimal senderPreTradeWantBalance = sender.getConfirmedBalance(wantAssetId);

		CreateAssetOrderTransactionData createOrderTransactionData = new CreateAssetOrderTransactionData(timestamp, Group.NO_GROUP, reference,
				sender.getPublicKey(), haveAssetId, wantAssetId, amount, price, fee);
		Transaction createOrderTransaction = new CreateAssetOrderTransaction(this.repository, createOrderTransactionData);
		createOrderTransaction.sign(sender);
		assertTrue(createOrderTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, createOrderTransaction.isValid());

		// Forge new block with transaction
		Block block = new Block(repository, parentBlockData, generator);
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

		// Check order has trades
		List<TradeData> trades = assetRepo.getOrdersTrades(orderId);
		assertNotNull(trades);
		assertEquals("Trade didn't happen", 1, trades.size());
		TradeData tradeData = trades.get(0);

		// Check trade has correct values
		BigDecimal expectedAmount = amount.divide(originalOrderData.getPrice()).setScale(8);
		BigDecimal actualAmount = tradeData.getTargetAmount();
		assertTrue(expectedAmount.compareTo(actualAmount) == 0);

		BigDecimal expectedPrice = amount;
		BigDecimal actualPrice = tradeData.getInitiatorAmount();
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
		expectedBalance = senderPreTradeWantBalance.subtract(BigDecimal.ONE).add(expectedAmount);
		actualBalance = sender.getConfirmedBalance(wantAssetId);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);

		// Check seller's order is correctly fulfilled
		assertTrue(orderData.getIsFulfilled());

		// Check buyer's order is still not fulfilled
		OrderData buyersOrderData = assetRepo.fromOrderId(originalOrderData.getOrderId());
		assertFalse(buyersOrderData.getIsFulfilled());

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

		MultiPaymentTransactionData multiPaymentTransactionData = new MultiPaymentTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(),
				payments, fee);

		Transaction multiPaymentTransaction = new MultiPaymentTransaction(repository, multiPaymentTransactionData);
		multiPaymentTransaction.sign(sender);
		assertTrue(multiPaymentTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, multiPaymentTransaction.isValid());

		// Forge new block with payment transaction
		Block block = new Block(repository, parentBlockData, generator);
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

		MessageTransactionData messageTransactionData = new MessageTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(), version,
				recipient.getAddress(), Asset.QORA, amount, data, isText, isEncrypted, fee);

		Transaction messageTransaction = new MessageTransaction(repository, messageTransactionData);
		messageTransaction.sign(sender);
		assertTrue(messageTransaction.isSignatureValid());
		assertEquals(ValidationResult.OK, messageTransaction.isValid());

		// Forge new block with message transaction
		Block block = new Block(repository, parentBlockData, generator);
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