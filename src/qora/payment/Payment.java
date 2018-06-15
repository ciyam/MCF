package qora.payment;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import data.PaymentData;
import data.assets.AssetData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.crypto.Crypto;
import qora.transaction.Transaction.ValidationResult;
import repository.AssetRepository;
import repository.DataException;
import repository.Repository;

public class Payment {

	// Properties
	private Repository repository;

	// Constructors

	public Payment(Repository repository) {
		this.repository = repository;
	}

	// Processing

	// Validate multiple payments
	public ValidationResult isValid(byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee, boolean isZeroAmountValid) throws DataException {
		AssetRepository assetRepository = this.repository.getAssetRepository();

		// Check fee is positive
		if (fee.compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Total up payment amounts by assetId
		Map<Long, BigDecimal> amountsByAssetId = new HashMap<Long, BigDecimal>();
		// Add transaction fee to start with
		amountsByAssetId.put(Asset.QORA, fee);

		// Check payments, and calculate amount total by assetId
		for (PaymentData paymentData : payments) {
			// Check amount is positive
			if (paymentData.getAmount().compareTo(BigDecimal.ZERO) < 0)
				return ValidationResult.NEGATIVE_AMOUNT;

			// Optional zero-amount check
			if (!isZeroAmountValid && paymentData.getAmount().compareTo(BigDecimal.ZERO) <= 0)
				return ValidationResult.NEGATIVE_AMOUNT;

			// Check recipient address is valid
			if (!Crypto.isValidAddress(paymentData.getRecipient()))
				return ValidationResult.INVALID_ADDRESS;

			AssetData assetData = assetRepository.fromAssetId(paymentData.getAssetId());
			// Check asset even exists
			if (assetData == null)
				return ValidationResult.ASSET_DOES_NOT_EXIST;

			// Check asset amount is integer if asset is not divisible
			if (!assetData.getIsDivisible() && paymentData.getAmount().stripTrailingZeros().scale() > 0)
				return ValidationResult.INVALID_AMOUNT;

			amountsByAssetId.compute(paymentData.getAssetId(), (assetId, amount) -> amount == null ? amount : amount.add(paymentData.getAmount()));
		}

		// Check sender has enough of each asset
		Account sender = new PublicKeyAccount(this.repository, senderPublicKey);
		for (Entry<Long, BigDecimal> pair : amountsByAssetId.entrySet())
			if (sender.getConfirmedBalance(pair.getKey()).compareTo(pair.getValue()) == -1)
				return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	public ValidationResult isValid(byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee) throws DataException {
		return isValid(senderPublicKey, payments, fee, false);
	}

	// Single payment forms
	public ValidationResult isValid(byte[] senderPublicKey, PaymentData paymentData, BigDecimal fee, boolean isZeroAmountValid) throws DataException {
		return isValid(senderPublicKey, Collections.singletonList(paymentData), fee);
	}

	public ValidationResult isValid(byte[] senderPublicKey, PaymentData paymentData, BigDecimal fee) throws DataException {
		return isValid(senderPublicKey, paymentData, fee, false);
	}

	public void process(byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee, byte[] signature) throws DataException {
		Account sender = new PublicKeyAccount(this.repository, senderPublicKey);

		// Update sender's balance due to fee
		sender.setConfirmedBalance(Asset.QORA, sender.getConfirmedBalance(Asset.QORA).subtract(fee));

		// Update sender's reference
		sender.setLastReference(signature);

		// Process all payments
		for (PaymentData paymentData : payments) {
			Account recipient = new Account(this.repository, paymentData.getRecipient());
			long assetId = paymentData.getAssetId();
			BigDecimal amount = paymentData.getAmount();

			// Update sender's balance due to amount
			sender.setConfirmedBalance(assetId, sender.getConfirmedBalance(assetId).subtract(amount));

			// Update recipient's balance
			recipient.setConfirmedBalance(assetId, recipient.getConfirmedBalance(assetId).add(amount));

			// For QORA amounts only: if recipient has no reference yet, then this is their starting reference
			if (assetId == Asset.QORA && recipient.getLastReference() == null)
				recipient.setLastReference(signature);
		}
	}

	public void process(byte[] senderPublicKey, PaymentData paymentData, BigDecimal fee, byte[] signature) throws DataException {
		process(senderPublicKey, Collections.singletonList(paymentData), fee, signature);
	}

	public void orphan(byte[] senderPublicKey, List<PaymentData> payments, BigDecimal fee, byte[] signature, byte[] reference) throws DataException {
		Account sender = new PublicKeyAccount(this.repository, senderPublicKey);

		// Update sender's balance due to fee
		sender.setConfirmedBalance(Asset.QORA, sender.getConfirmedBalance(Asset.QORA).subtract(fee));

		// Update sender's reference
		sender.setLastReference(reference);

		for (PaymentData paymentData : payments) {
			Account recipient = new Account(this.repository, paymentData.getRecipient());
			long assetId = paymentData.getAssetId();
			BigDecimal amount = paymentData.getAmount();

			// Update sender's balance due to amount
			sender.setConfirmedBalance(assetId, sender.getConfirmedBalance(assetId).add(amount));

			// Update recipient's balance
			recipient.setConfirmedBalance(assetId, recipient.getConfirmedBalance(assetId).subtract(amount));

			/*
			 * For QORA amounts only: If recipient's last reference is this transaction's signature, then they can't have made any transactions of their own
			 * (which would have changed their last reference) thus this is their first reference so remove it.
			 */
			if (assetId == Asset.QORA && Arrays.equals(recipient.getLastReference(), signature))
				recipient.setLastReference(null);
		}
	}

	public void orphan(byte[] senderPublicKey, PaymentData paymentData, BigDecimal fee, byte[] signature, byte[] reference) throws DataException {
		orphan(senderPublicKey, Collections.singletonList(paymentData), fee, signature, reference);
	}

}
