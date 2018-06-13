package qora.transaction;

import data.transaction.TransactionData;

import qora.assets.Order;
import repository.DataException;
import repository.Repository;

public class CreateOrderTransaction extends Transaction {

	// Properties

	// Constructors

	public CreateOrderTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);
	}

	// Navigation

	public Order getOrder() {
		// TODO Something like:
		// return this.repository.getAssetRepository().getOrder(this.transactionData);
		return null;
	}

	// Processing

	public ValidationResult isValid() throws DataException {
		// TODO

		return ValidationResult.OK;
	}

	public void process() throws DataException {
		// TODO
	}

	public void orphan() throws DataException {
		// TODO
	}

}
