package qora.naming;

import data.naming.NameData;
import data.transaction.RegisterNameTransactionData;
import repository.DataException;
import repository.Repository;

public class Name {

	// Properties
	private Repository repository;
	private NameData nameData;

	// Useful constants
	public static final int MAX_NAME_SIZE = 400;
	public static final int MAX_VALUE_SIZE = 4000;

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
				registerNameTransactionData.getName(), registerNameTransactionData.getData(), registerNameTransactionData.getTimestamp());
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

}
