package org.qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.block.BlockChain;
import org.qora.data.PaymentData;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.payment.Payment;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class ArbitraryTransaction extends Transaction {

	// Properties
	private ArbitraryTransactionData arbitraryTransactionData;

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
		// We would save updated transaction at this point, but it hasn't been modified

		// Wrap and delegate payment processing to Payment class. Always update recipients' last references regardless of asset.
		new Payment(this.repository).process(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee(), arbitraryTransactionData.getSignature(), true);
	}

	@Override
	public void orphan() throws DataException {
		// Wrap and delegate payment processing to Payment class. Always revert recipients' last references regardless of asset.
		new Payment(this.repository).orphan(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee(), arbitraryTransactionData.getSignature(), arbitraryTransactionData.getReference(), true);

		// We would save transaction in orphaned form at this point, but it hasn't been modified
	}

	// Data access

	public boolean isDataLocal() throws DataException {
		return this.repository.getArbitraryRepository().isDataLocal(this.transactionData.getSignature());
	}

	public byte[] fetchData() throws DataException {
		// If local, read from file
		if (isDataLocal())
			return this.repository.getArbitraryRepository().fetchData(this.transactionData.getSignature());

		// TODO If not local, attempt to fetch via network?
		return null;
	}

}
