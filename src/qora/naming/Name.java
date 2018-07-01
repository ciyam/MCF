package qora.naming;

import data.naming.NameData;
import data.transaction.RegisterNameTransactionData;
import data.transaction.TransactionData;
import data.transaction.UpdateNameTransactionData;
import repository.DataException;
import repository.Repository;

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
		this.nameData = new NameData(registerNameTransactionData.getRegistrantPublicKey(), registerNameTransactionData.getOwner(),
				registerNameTransactionData.getName(), registerNameTransactionData.getData(), registerNameTransactionData.getTimestamp(),
				registerNameTransactionData.getSignature());
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
		TransactionData previousTransactionData = this.repository.getTransactionRepository().fromSignature(this.nameData.getReference());
		if (previousTransactionData == null)
			throw new DataException("Unable to un-update name as referenced transaction not found in repository");

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

			default:
				throw new IllegalStateException("Unable to revert update name transaction due to unsupported referenced transaction");
		}

		// Save reverted name data
		this.repository.getNameRepository().save(this.nameData);
	}

}
