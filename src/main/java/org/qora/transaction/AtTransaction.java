package org.qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.qora.account.Account;
import org.qora.asset.Asset;
import org.qora.crypto.Crypto;
import org.qora.data.asset.AssetData;
import org.qora.data.transaction.ATTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.transform.TransformationException;
import org.qora.transform.transaction.AtTransactionTransformer;

import com.google.common.primitives.Bytes;

public class AtTransaction extends Transaction {

	// Properties
	private ATTransactionData atTransactionData;

	// Other useful constants
	public static final int MAX_DATA_SIZE = 256;

	// Constructors

	public AtTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.atTransactionData = (ATTransactionData) this.transactionData;

		// Check whether we need to generate the ATTransaction's pseudo-signature
		if (this.atTransactionData.getSignature() == null) {
			// Signature is SHA2-256 of serialized transaction data, duplicated to make standard signature size of 64 bytes.
			try {
				byte[] digest = Crypto.digest(AtTransactionTransformer.toBytes(transactionData));
				byte[] signature = Bytes.concat(digest, digest);
				this.atTransactionData.setSignature(signature);
			} catch (TransformationException e) {
				throw new RuntimeException("Couldn't transform AT Transaction into bytes", e);
			}
		}
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() throws DataException {
		return Collections.singletonList(new Account(this.repository, this.atTransactionData.getRecipient()));
	}

	/** For AT-Transactions, the use the AT address instead of transaction creator (which is genesis account) */
	@Override
	public List<Account> getInvolvedAccounts() throws DataException {
		List<Account> participants = new ArrayList<Account>(getRecipientAccounts());
		participants.add(getATAccount());
		return participants;
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		String address = account.getAddress();

		if (address.equals(this.atTransactionData.getATAddress()))
			return true;

		if (address.equals(this.atTransactionData.getRecipient()))
			return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		String address = account.getAddress();
		BigDecimal amount = BigDecimal.ZERO.setScale(8);
		String atAddress = this.atTransactionData.getATAddress();

		if (address.equals(atAddress)) {
			amount = amount.subtract(this.atTransactionData.getFee());

			if (this.atTransactionData.getAmount() != null && this.atTransactionData.getAssetId() == Asset.QORA)
				amount = amount.subtract(this.atTransactionData.getAmount());
		}

		if (address.equals(this.atTransactionData.getRecipient()) && this.atTransactionData.getAmount() != null
				&& this.atTransactionData.getAssetId() == Asset.QORA)
			amount = amount.add(this.atTransactionData.getAmount());

		return amount;
	}

	// Navigation

	public Account getATAccount() throws DataException {
		return new Account(this.repository, this.atTransactionData.getATAddress());
	}

	public Account getRecipient() throws DataException {
		return new Account(this.repository, this.atTransactionData.getRecipient());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check reference is correct
		Account atAccount = getATAccount();
		if (!Arrays.equals(atAccount.getLastReference(), atTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		if (this.atTransactionData.getMessage().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		BigDecimal amount = this.atTransactionData.getAmount();
		byte[] message = this.atTransactionData.getMessage();

		// We can only have either message or amount
		boolean amountIsZero = amount.compareTo(BigDecimal.ZERO.setScale(8)) == 0;
		boolean messageIsEmpty = message.length == 0;

		if ((messageIsEmpty && amountIsZero) || (!messageIsEmpty && !amountIsZero))
			return ValidationResult.INVALID_AT_TRANSACTION;

		// If we have no payment then we're done
		if (amountIsZero)
			return ValidationResult.OK;

		// Check amount is zero or positive
		if (amount.compareTo(BigDecimal.ZERO) < 0)
			return ValidationResult.NEGATIVE_AMOUNT;

		// Check recipient address is valid
		if (!Crypto.isValidAddress(this.atTransactionData.getRecipient()))
			return ValidationResult.INVALID_ADDRESS;

		long assetId = this.atTransactionData.getAssetId();
		AssetData assetData = this.repository.getAssetRepository().fromAssetId(assetId);
		// Check asset even exists
		if (assetData == null)
			return ValidationResult.ASSET_DOES_NOT_EXIST;

		// Check asset amount is integer if asset is not divisible
		if (!assetData.getIsDivisible() && amount.stripTrailingZeros().scale() > 0)
			return ValidationResult.INVALID_AMOUNT;

		Account sender = getATAccount();
		// Check sender has enough of asset
		if (sender.getConfirmedBalance(assetId).compareTo(amount) < 0)
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		if (this.atTransactionData.getAmount() != null) {
			Account sender = getATAccount();
			Account recipient = getRecipient();

			long assetId = this.atTransactionData.getAssetId();
			BigDecimal amount = this.atTransactionData.getAmount();

			// Update sender's balance due to amount
			sender.setConfirmedBalance(assetId, sender.getConfirmedBalance(assetId).subtract(amount));

			// Update recipient's balance
			recipient.setConfirmedBalance(assetId, recipient.getConfirmedBalance(assetId).add(amount));

			// For QORA amounts only: if recipient has no reference yet, then this is their starting reference
			if (assetId == Asset.QORA && recipient.getLastReference() == null)
				// In Qora1 last reference was set to 64-bytes of zero
				// In Qora2 we use AT-Transction's signature, which makes more sense
				recipient.setLastReference(this.atTransactionData.getSignature());
		}
	}

	@Override
	public void orphan() throws DataException {
		// Delete this transaction
		this.repository.getTransactionRepository().delete(this.transactionData);

		if (this.atTransactionData.getAmount() != null) {
			Account sender = getATAccount();
			Account recipient = getRecipient();

			long assetId = this.atTransactionData.getAssetId();
			BigDecimal amount = this.atTransactionData.getAmount();

			// Update sender's balance due to amount
			sender.setConfirmedBalance(assetId, sender.getConfirmedBalance(assetId).add(amount));

			// Update recipient's balance
			recipient.setConfirmedBalance(assetId, recipient.getConfirmedBalance(assetId).subtract(amount));

			/*
			 * For QORA amounts only: If recipient's last reference is this transaction's signature, then they can't have made any transactions of their own
			 * (which would have changed their last reference) thus this is their first reference so remove it.
			 */
			if (assetId == Asset.QORA && Arrays.equals(recipient.getLastReference(), this.atTransactionData.getSignature()))
				recipient.setLastReference(null);
		}
	}

}
