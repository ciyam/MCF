package repository.hsqldb;

import repository.Repository;

public class HSQLDBRepository extends Repository {

	public HSQLDBRepository() {
		this.transactionRepository = new HSQLDBTransaction();
	}

}
