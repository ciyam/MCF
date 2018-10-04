package qora.at;

import org.ciyam.at.MachineState;

import data.at.ATData;
import data.at.ATStateData;
import data.transaction.DeployATTransactionData;
import repository.DataException;
import repository.Repository;

public class AT {

	// Properties
	private Repository repository;
	private ATData atData;
	private ATStateData atStateData;

	// Constructors

	public AT(Repository repository, ATData atData, ATStateData atStateData) {
		this.repository = repository;
		this.atData = atData;
		this.atStateData = atStateData;
	}

	public AT(Repository repository, DeployATTransactionData deployATTransactionData) throws DataException {
		this.repository = repository;

		MachineState machineState = new MachineState(deployATTransactionData.getCreationBytes());

		this.atData = new ATData(deployATTransactionData.getATAddress(), machineState.version, machineState.codeByteBuffer.array(), machineState.isSleeping,
				machineState.sleepUntilHeight, machineState.isFinished, machineState.hadFatalError, machineState.isFrozen, machineState.frozenBalance,
				deployATTransactionData.getSignature());

		String atAddress = this.atData.getATAddress();

		int height = this.repository.getBlockRepository().getBlockchainHeight();
		byte[] stateData = machineState.toBytes();

		this.atStateData = new ATStateData(atAddress, height, stateData);
	}

	public void deploy() throws DataException {
		this.repository.getATRepository().save(this.atData);
		this.repository.getATRepository().save(this.atStateData);
	}

	public void undeploy() throws DataException {
		// AT states deleted implicitly by repository
		this.repository.getATRepository().delete(this.atData.getATAddress());
	}
}
