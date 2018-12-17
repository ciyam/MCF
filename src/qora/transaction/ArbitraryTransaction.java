package qora.transaction;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import data.PaymentData;
import data.transaction.ArbitraryTransactionData;
import data.transaction.TransactionData;
import data.transaction.ArbitraryTransactionData.DataType;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.block.BlockChain;
import qora.crypto.Crypto;
import qora.payment.Payment;
import repository.DataException;
import repository.Repository;
import settings.Settings;
import utils.Base58;

public class ArbitraryTransaction extends Transaction {

	// Properties
	private ArbitraryTransactionData arbitraryTransactionData;

	// Other properties
	private static final Logger LOGGER = LogManager.getLogger(ArbitraryTransaction.class);

	// Other useful constants
	public static final int MAX_DATA_SIZE = 4000;

	// Constructors

	public ArbitraryTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.arbitraryTransactionData = (ArbitraryTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		List<Account> recipients = new ArrayList<Account>();

		if (arbitraryTransactionData.getVersion() != 1)
			for (PaymentData paymentData : arbitraryTransactionData.getPayments())
				recipients.add(new Account(this.repository, paymentData.getRecipient()));

		return recipients;
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.getSender().getAddress()))
			return true;

		if (arbitraryTransactionData.getVersion() != 1)
			for (PaymentData paymentData : arbitraryTransactionData.getPayments())
				if (address.equals(paymentData.getRecipient()))
					return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);
		String senderAddress = this.getSender().getAddress();

		if (address.equals(senderAddress))
			amount = amount.subtract(this.transactionData.getFee());

		if (arbitraryTransactionData.getVersion() != 1)
			for (PaymentData paymentData : arbitraryTransactionData.getPayments())
				// We're only interested in QORA
				if (paymentData.getAssetId() == Asset.QORA) {
					if (address.equals(paymentData.getRecipient()))
						amount = amount.add(paymentData.getAmount());
					else if (address.equals(senderAddress))
						amount = amount.subtract(paymentData.getAmount());
				}

		return amount;
	}

	// Navigation

	public Account getSender() throws DataException {
		return new PublicKeyAccount(this.repository, this.arbitraryTransactionData.getSenderPublicKey());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Are arbitrary transactions even allowed at this point?
		if (arbitraryTransactionData.getVersion() != ArbitraryTransaction.getVersionByTimestamp(arbitraryTransactionData.getTimestamp()))
			return ValidationResult.NOT_YET_RELEASED;

		if (this.arbitraryTransactionData.getTimestamp() < BlockChain.getInstance().getArbitraryReleaseTimestamp())
			return ValidationResult.NOT_YET_RELEASED;

		// Check data length
		if (arbitraryTransactionData.getData().length < 1 || arbitraryTransactionData.getData().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check reference is correct
		Account sender = getSender();
		if (!Arrays.equals(sender.getLastReference(), arbitraryTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee());
	}

	@Override
	public void process() throws DataException {
		/*
		 * We might have either raw data or only a hash of data, depending on content filtering.
		 * 
		 * If we have raw data then we need to save it somewhere and store the hash in the repository.
		 */
		if (arbitraryTransactionData.getDataType() == DataType.RAW_DATA) {
			byte[] rawData = arbitraryTransactionData.getData();

			// Calculate hash of data and update our transaction to use that
			byte[] dataHash = Crypto.digest(rawData);
			arbitraryTransactionData.setData(dataHash);
			arbitraryTransactionData.setDataType(DataType.DATA_HASH);

			// Now store actual data somewhere, e.g. <userpath>/arbitrary/<sender address>/<block height>/<tx-sig>-<service>.raw
			Account sender = this.getSender();
			int blockHeight = this.repository.getBlockRepository().getBlockchainHeight();

			String senderPathname = Settings.getInstance().getUserpath() + "arbitrary" + File.separator + sender.getAddress();
			String blockPathname = senderPathname + File.separator + blockHeight;
			String dataPathname = blockPathname + File.separator + Base58.encode(arbitraryTransactionData.getSignature()) + "-"
					+ arbitraryTransactionData.getService() + ".raw";

			Path dataPath = Paths.get(dataPathname);

			// Make sure directory structure exists
			try {
				Files.createDirectories(dataPath.getParent());
			} catch (IOException e) {
				LOGGER.error("Unable to create arbitrary transaction directory", e);
				throw new DataException("Unable to create arbitrary transaction directory", e);
			}

			// Output actual transaction data
			try (OutputStream dataOut = Files.newOutputStream(dataPath)) {
				dataOut.write(rawData);
			} catch (IOException e) {
				LOGGER.error("Unable to store arbitrary transaction data", e);
				throw new DataException("Unable to store arbitrary transaction data", e);
			}
		}

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Wrap and delegate payment processing to Payment class. Always update recipients' last references regardless of asset.
		new Payment(this.repository).process(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee(), arbitraryTransactionData.getSignature(), true);
	}

	@Override
	public void orphan() throws DataException {
		// Delete corresponding data file (if any - storing raw data is optional)
		Account sender = this.getSender();
		int blockHeight = this.repository.getBlockRepository().getBlockchainHeight();

		String senderPathname = Settings.getInstance().getUserpath() + "arbitrary" + File.separator + sender.getAddress();
		String blockPathname = senderPathname + File.separator + blockHeight;
		String dataPathname = blockPathname + File.separator + Base58.encode(arbitraryTransactionData.getSignature()) + "-"
				+ arbitraryTransactionData.getService() + ".raw";

		try {
			// Delete the actual arbitrary data
			Files.delete(Paths.get(dataPathname));

			// If block-directory now empty, delete that too
			Files.delete(Paths.get(blockPathname));

			// If sender-directory now empty, delete that too
			Files.delete(Paths.get(senderPathname));
		} catch (NoSuchFileException e) {
			LOGGER.warn("Unable to remove old arbitrary transaction data at " + dataPathname);
		} catch (DirectoryNotEmptyException e) {
			// This happens when block-directory or sender-directory is not empty but is OK
		} catch (IOException e) {
			LOGGER.warn("IOException when trying to remove old arbitrary transaction data", e);
		}

		// Delete this transaction itself
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Wrap and delegate payment processing to Payment class. Always revert recipients' last references regardless of asset.
		new Payment(this.repository).orphan(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee(), arbitraryTransactionData.getSignature(), arbitraryTransactionData.getReference(), true);
	}

}
