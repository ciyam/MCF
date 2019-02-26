package org.qora.naming;

import org.qora.account.Account;
import org.qora.account.PublicKeyAccount;
import org.qora.asset.Asset;
import org.qora.data.naming.NameData;
import org.qora.data.transaction.BuyNameTransactionData;
import org.qora.data.transaction.CancelSellNameTransactionData;
import org.qora.data.transaction.RegisterNameTransactionData;
import org.qora.data.transaction.SellNameTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.data.transaction.UpdateNameTransactionData;
import org.qora.repository.DataException;
import org.qora.repository.Repository;

public class Name {

	// Properties
	private Repository repository;
	private NameData nameData;

	// Useful constants
	public static final int MAX_NAME_SIZE = 400;
	public static final int MAX_DATA_SIZE = 4000;

	// Constructors

	/**
	 * Construct Name business object using info from register name transaction.
	 * 
	 * @param repository
	 * @param registerNameTransactionData
	 */
	public Name(Repository repository, RegisterNameTransactionData registerNameTransactionData) {
		this.repository = repository;
		this.nameData = new NameData(registerNameTransactionData.getOwner(),
				registerNameTransactionData.getName(), registerNameTransactionData.getData(), registerNameTransactionData.getTimestamp(),
				registerNameTransactionData.getSignature(), registerNameTransactionData.getTxGroupId());
	}

	/**
	 * Construct Name business object using existing name in repository.
	 * 
	 * @param repository
	 * @param name
	 * @throws DataException
	 */
	public Name(Repository repository, String name) throws DataException {
		this.repository = repository;
		this.nameData = this.repository.getNameRepository().fromName(name);
	}

	// Processing

	public void register() throws DataException {
		this.repository.getNameRepository().save(this.nameData);
	}

	public void unregister() throws DataException {
		this.repository.getNameRepository().delete(this.nameData.getName());
	}

	private void revert() throws DataException {
		TransactionData previousTransactionData = this.repository.getTransactionRepository().fromSignature(this.nameData.getReference());
		if (previousTransactionData == null)
			throw new DataException("Unable to revert name transaction as referenced transaction not found in repository");

		switch (previousTransactionData.getType()) {
			case REGISTER_NAME:
				RegisterNameTransactionData previousRegisterNameTransactionData = (RegisterNameTransactionData) previousTransactionData;
				this.nameData.setOwner(previousRegisterNameTransactionData.getOwner());
				this.nameData.setData(previousRegisterNameTransactionData.getData());
				break;

			case UPDATE_NAME:
				UpdateNameTransactionData previousUpdateNameTransactionData = (UpdateNameTransactionData) previousTransactionData;
				this.nameData.setData(previousUpdateNameTransactionData.getNewData());
				this.nameData.setOwner(previousUpdateNameTransactionData.getNewOwner());
				break;

			case BUY_NAME:
				BuyNameTransactionData previousBuyNameTransactionData = (BuyNameTransactionData) previousTransactionData;
				Account buyer = new PublicKeyAccount(this.repository, previousBuyNameTransactionData.getBuyerPublicKey());
				this.nameData.setOwner(buyer.getAddress());
				break;

			default:
				throw new IllegalStateException("Unable to revert name transaction due to unsupported referenced transaction");
		}
	}

	public void update(UpdateNameTransactionData updateNameTransactionData) throws DataException {
		// Update reference in transaction data
		updateNameTransactionData.setNameReference(this.nameData.getReference());

		// New name reference is this transaction's signature
		this.nameData.setReference(updateNameTransactionData.getSignature());

		// Update Name's owner and data
		this.nameData.setOwner(updateNameTransactionData.getNewOwner());
		this.nameData.setData(updateNameTransactionData.getNewData());

		// Save updated name data
		this.repository.getNameRepository().save(this.nameData);
	}

	public void revert(UpdateNameTransactionData updateNameTransactionData) throws DataException {
		// Previous name reference is taken from this transaction's cached copy
		this.nameData.setReference(updateNameTransactionData.getNameReference());

		// Previous Name's owner and/or data taken from referenced transaction
		this.revert();

		// Save reverted name data
		this.repository.getNameRepository().save(this.nameData);
	}

	public void sell(SellNameTransactionData sellNameTransactionData) throws DataException {
		// Mark as for-sale and set price
		this.nameData.setIsForSale(true);
		this.nameData.setSalePrice(sellNameTransactionData.getAmount());

		// Save sale info into repository
		this.repository.getNameRepository().save(this.nameData);
	}

	public void unsell(SellNameTransactionData sellNameTransactionData) throws DataException {
		// Mark not for-sale and unset price
		this.nameData.setIsForSale(false);
		this.nameData.setSalePrice(null);

		// Save no-sale info into repository
		this.repository.getNameRepository().save(this.nameData);
	}

	public void sell(CancelSellNameTransactionData cancelSellNameTransactionData) throws DataException {
		// Mark not for-sale but leave price in case we want to orphan
		this.nameData.setIsForSale(false);

		// Save sale info into repository
		this.repository.getNameRepository().save(this.nameData);
	}

	public void unsell(CancelSellNameTransactionData cancelSellNameTransactionData) throws DataException {
		// Mark as for-sale using existing price
		this.nameData.setIsForSale(true);

		// Save no-sale info into repository
		this.repository.getNameRepository().save(this.nameData);
	}

	public void buy(BuyNameTransactionData buyNameTransactionData) throws DataException {
		// Mark not for-sale but leave price in case we want to orphan
		this.nameData.setIsForSale(false);

		// Update seller's balance
		Account seller = new Account(this.repository, this.nameData.getOwner());
		seller.setConfirmedBalance(Asset.QORA, seller.getConfirmedBalance(Asset.QORA).add(buyNameTransactionData.getAmount()));

		// Set new owner
		Account buyer = new PublicKeyAccount(this.repository, buyNameTransactionData.getBuyerPublicKey());
		this.nameData.setOwner(buyer.getAddress());
		// Update buyer's balance
		buyer.setConfirmedBalance(Asset.QORA, buyer.getConfirmedBalance(Asset.QORA).subtract(buyNameTransactionData.getAmount()));

		// Update reference in transaction data
		buyNameTransactionData.setNameReference(this.nameData.getReference());

		// New name reference is this transaction's signature
		this.nameData.setReference(buyNameTransactionData.getSignature());

		// Save updated name data
		this.repository.getNameRepository().save(this.nameData);
	}

	public void unbuy(BuyNameTransactionData buyNameTransactionData) throws DataException {
		// Mark as for-sale using existing price
		this.nameData.setIsForSale(true);

		// Previous name reference is taken from this transaction's cached copy
		this.nameData.setReference(buyNameTransactionData.getNameReference());

		// Revert buyer's balance
		Account buyer = new PublicKeyAccount(this.repository, buyNameTransactionData.getBuyerPublicKey());
		buyer.setConfirmedBalance(Asset.QORA, buyer.getConfirmedBalance(Asset.QORA).add(buyNameTransactionData.getAmount()));

		// Previous Name's owner and/or data taken from referenced transaction
		this.revert();

		// Revert seller's balance
		Account seller = new Account(this.repository, this.nameData.getOwner());
		seller.setConfirmedBalance(Asset.QORA, seller.getConfirmedBalance(Asset.QORA).subtract(buyNameTransactionData.getAmount()));

		// Save reverted name data
		this.repository.getNameRepository().save(this.nameData);
	}

}
