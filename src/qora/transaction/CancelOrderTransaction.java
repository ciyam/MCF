package qora.transaction;

import java.math.BigDecimal;
import java.util.Arrays;

import data.assets.OrderData;
import data.transaction.CancelOrderTransactionData;
import data.transaction.TransactionData;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.assets.Order;
import qora.crypto.Crypto;
import repository.AssetRepository;
import repository.DataException;
import repository.Repository;

public class CancelOrderTransaction extends Transaction {

	// Constructors

	public CancelOrderTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		CancelOrderTransactionData cancelOrderTransactionData = (CancelOrderTransactionData) this.transactionData;
		AssetRepository assetRepository = this.repository.getAssetRepository();

		// Check fee is positive
		if (cancelOrderTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check order even exists
		OrderData orderData = assetRepository.fromOrderId(cancelOrderTransactionData.getOrderId());

		if (orderData == null)
			return ValidationResult.ORDER_DOES_NOT_EXIST;

		Account creator = new PublicKeyAccount(this.repository, cancelOrderTransactionData.getCreatorPublicKey());

		// Check creator's public key results in valid address
		if (!Crypto.isValidAddress(creator.getAddress()))
			return ValidationResult.INVALID_ADDRESS;

		// Check creator's public key matches order's creator's public key
		Account orderCreator = new PublicKeyAccount(this.repository, orderData.getCreatorPublicKey());
		if (!orderCreator.getAddress().equals(creator.getAddress()))
			return ValidationResult.INVALID_ORDER_CREATOR;

		// Check creator has enough QORA for fee
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(cancelOrderTransactionData.getFee()) == -1)
			return ValidationResult.NO_BALANCE;

		// Check reference is correct
		if (!Arrays.equals(creator.getLastReference(), cancelOrderTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		return ValidationResult.OK;
	}

	// PROCESS/ORPHAN

	@Override
	public void process() throws DataException {
		CancelOrderTransactionData cancelOrderTransactionData = (CancelOrderTransactionData) this.transactionData;
		Account creator = new PublicKeyAccount(this.repository, cancelOrderTransactionData.getCreatorPublicKey());

		// Save this transaction itself
		this.repository.getTransactionRepository().save(this.transactionData);

		// Update creator's balance regarding fee
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).subtract(cancelOrderTransactionData.getFee()));

		// Update creator's last reference
		creator.setLastReference(cancelOrderTransactionData.getSignature());

		// Mark Order as completed so no more trades can happen
		OrderData orderData = this.repository.getAssetRepository().fromOrderId(cancelOrderTransactionData.getOrderId());
		Order order = new Order(this.repository, orderData);
		order.cancel();

		// Update creator's balance with unfulfilled amount
		creator.setConfirmedBalance(orderData.getHaveAssetId(), creator.getConfirmedBalance(orderData.getHaveAssetId()).add(order.getAmountLeft()));
	}

	@Override
	public void orphan() throws DataException {
		CancelOrderTransactionData cancelOrderTransactionData = (CancelOrderTransactionData) this.transactionData;
		Account creator = new PublicKeyAccount(this.repository, cancelOrderTransactionData.getCreatorPublicKey());

		// Save this transaction itself
		this.repository.getTransactionRepository().delete(this.transactionData);

		// Update creator's balance regarding fee
		creator.setConfirmedBalance(Asset.QORA, creator.getConfirmedBalance(Asset.QORA).add(cancelOrderTransactionData.getFee()));

		// Update creator's last reference
		creator.setLastReference(cancelOrderTransactionData.getReference());

		// Unmark Order as completed so trades can happen again
		OrderData orderData = this.repository.getAssetRepository().fromOrderId(cancelOrderTransactionData.getOrderId());
		Order order = new Order(this.repository, orderData);
		order.reopen();

		// Update creator's balance with unfulfilled amount
		creator.setConfirmedBalance(orderData.getHaveAssetId(), creator.getConfirmedBalance(orderData.getHaveAssetId()).subtract(order.getAmountLeft()));
	}

}
