package org.qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.asset.Order;
import org.qora.crypto.Crypto;
import org.qora.data.asset.OrderData;
import org.qora.data.transaction.CancelOrderTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.AssetRepository;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class CancelOrderTransaction extends Transaction {

	// Properties
	private CancelOrderTransactionData cancelOrderTransactionData;

	// Constructors

	public CancelOrderTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.cancelOrderTransactionData = (CancelOrderTransactionData) this.transactionData;
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
	public Account getCreator() throws DataException {
		return new PublicKeyAccount(this.repository, cancelOrderTransactionData.getCreatorPublicKey());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		AssetRepository assetRepository = this.repository.getAssetRepository();

		// Check fee is positive
		if (cancelOrderTransactionData.getFee().compareTo(BigDecimal.ZERO) <= 0)
			return ValidationResult.NEGATIVE_FEE;

		// Check order even exists
		OrderData orderData = assetRepository.fromOrderId(cancelOrderTransactionData.getOrderId());

		if (orderData == null)
			return ValidationResult.ORDER_DOES_NOT_EXIST;

		Account creator = getCreator();

		// Check creator's public key results in valid address
		if (!Crypto.isValidAddress(creator.getAddress()))
			return ValidationResult.INVALID_ADDRESS;

		// Check creator's public key matches order's creator's public key
		Account orderCreator = new PublicKeyAccount(this.repository, orderData.getCreatorPublicKey());
		if (!orderCreator.getAddress().equals(creator.getAddress()))
			return ValidationResult.INVALID_ORDER_CREATOR;

		// Check creator has enough QORA for fee
		if (creator.getConfirmedBalance(Asset.QORA).compareTo(cancelOrderTransactionData.getFee()) < 0)
			return ValidationResult.NO_BALANCE;

		// Check reference is correct
		if (!Arrays.equals(creator.getLastReference(), cancelOrderTransactionData.getReference()))
			return ValidationResult.INVALID_REFERENCE;

		return ValidationResult.OK;
	}

	@Override
	public void process() throws DataException {
		Account creator = getCreator();

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
		Account creator = getCreator();

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
