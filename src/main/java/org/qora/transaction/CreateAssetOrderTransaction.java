package org.qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.asset.Order;
import org.qora.block.BlockChain;
import org.qora.data.asset.AssetData;
import org.qora.data.asset.OrderData;
import org.qora.data.transaction.CreateAssetOrderTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.AssetRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class CreateAssetOrderTransaction extends Transaction {

	// Properties
	private CreateAssetOrderTransactionData createOrderTransactionData;

	// Constructors

	public CreateAssetOrderTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.createOrderTransactionData = (CreateAssetOrderTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<Account> getRecipientAccounts() {
		return new ArrayList<Account>();
	}

	@Override
	public boolean isInvolved(Account account) throws DataException {
		return account.getAddress().equals(this.getCreator().getAddress());
	}

	@Override
	public BigDecimal getAmount(Account account) throws DataException {
		BigDecimal amount = BigDecimal.ZERO.setScale(8);

		if (account.getAddress().equals(this.getCreator().getAddress()))
			amount = amount.subtract(this.transactionData.getFee());

		return amount;
	}

	// Navigation

	@Override
	public PublicKeyAccount getCreator() throws DataException {
		return new PublicKeyAccount(this.repository, createOrderTransactionData.getCreatorPublicKey());
	}

	public Order getOrder() throws DataException {
		// orderId is the transaction signature
		OrderData orderData = this.repository.getAssetRepository().fromOrderId(this.createOrderTransactionData.getSignature());
		return new Order(this.repository, orderData);
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		long haveAssetId = createOrderTransactionData.getHaveAssetId();
		long wantAssetId = createOrderTransactionData.getWantAssetId();

		// Check have/want assets are not the same
		if (haveAssetId == wantAssetId)
			return ValidationResult.HAVE_EQUALS_WANT;

		// Check amount is positive
		if (createOrderTransactionData.getAmount().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_AMOUNT;

		// Check price is positive
		if (createOrderTransactionData.getPrice().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_PRICE;

		// Check fee is positive
		if (createOrderTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		AssetRepository assetRepository = this.repository.getAssetRepository();

		// Check "have" asset exists
		AssetData haveAssetData = assetRepository.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return ValidationResult.ASSET_DOES_NOT_EXIST;

		// Check "want" asset exists
		AssetData wantAssetData = assetRepository.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return ValidationResult.ASSET_DOES_NOT_EXIST;

		Account creator = getCreator();

		// Check reference is correct
		if (!Arrays.equals(creator.getLastReference(), createOrderTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		// Check order creator has enough asset balance AFTER removing fee, in case asset is QORA
		// If asset is QORA then we need to check amount + fee in one go
		if (haveAssetId == Asset.QORA) {
			// Check creator has enough funds for amount + fee in QORA
			if (creator.getConfirmedBalance(Asset.QORA).compareTo(createOrderTransactionData.getAmount().add(createOrderTransactionData.getFee())) < 0)
				return ValidationResult.NO_BALANCE;
		} else {
			// Check creator has enough funds for amount in whatever asset
			if (creator.getConfirmedBalance(haveAssetId).compareTo(createOrderTransactionData.getAmount()) < 0)
				return ValidationResult.NO_BALANCE;

			// Check creator has enough funds for fee in QORA
			// NOTE: in Gen1 pre-POWFIX-RELEASE transactions didn't have this check
			if (createOrderTransactionData.getTimestamp() >= BlockChain.getInstance().getPowFixReleaseTimestamp()
					&& creator.getConfirmedBalance(Asset.QORA).compareTo(createOrderTransactionData.getFee()) < 0)
				return ValidationResult.NO_BALANCE;
		}

		// Check "have" amount is integer if "have" asset is not divisible
		if (!haveAssetData.getIsDivisible() && createOrderTransactionData.getAmount().stripTrailingZeros().scale() > 0)
			return ValidationResult.INVALID_AMOUNT;

		// Check total return from fulfilled order would be integer if "want" asset is not divisible
		if (!wantAssetData.getIsDivisible()
				&& createOrderTransactionData.getAmount().multiply(createOrderTransactionData.getPrice()).stripTrailingZeros().scale() > 0)
			return ValidationResult.INVALID_RETURN;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		Account creator = getCreator();

		// Update creator's balance due to fee
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).subtract(createOrderTransactionData.getFee()));

		// Update creator's last reference
		creator.setLastReference(createOrderTransactionData.getSignature());

		// Save this transaction itself
		this.repository.getTransactionRepository().save(createOrderTransactionData);

		// Order Id is transaction's signature
		byte[] orderId = createOrderTransactionData.getSignature();

		// Process the order itself
		OrderData orderData = new OrderData(orderId, createOrderTransactionData.getCreatorPublicKey(), createOrderTransactionData.getHaveAssetId(),
				createOrderTransactionData.getWantAssetId(), createOrderTransactionData.getAmount(), createOrderTransactionData.getPrice(),
				createOrderTransactionData.getTimestamp());

		new Order(this.repository, orderData).process();
	}

	@Override
	public void orphan() throws DataException {
		Account creator = getCreator();

		// Update creator's balance due to fee
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(createOrderTransactionData.getFee()));

		// Update creator's last reference
		creator.setLastReference(createOrderTransactionData.getReference());

		// Order Id is transaction's signature
		byte[] orderId = createOrderTransactionData.getSignature();

		// Orphan the order itself
		OrderData orderData = this.repository.getAssetRepository().fromOrderId(orderId);
		new Order(this.repository, orderData).orphan();

		// Delete this transaction
		this.repository.getTransactionRepository().delete(createOrderTransactionData);
	}

}
