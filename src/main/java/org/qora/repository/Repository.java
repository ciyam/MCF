package org.qora.repository;

public interface Repository extends AutoCloseable {

	public ATRepository getATRepository();

	public AccountRepository getAccountRepository();

	public ArbitraryRepository getArbitraryRepository();

	public AssetRepository getAssetRepository();

	public BlockRepository getBlockRepository();

	public GroupRepository getGroupRepository();

	public NameRepository getNameRepository();

	public NetworkRepository getNetworkRepository();

	public TransactionRepository getTransactionRepository();

	public VotingRepository getVotingRepository();

	public void saveChanges() throws DataException;

	public void discardChanges() throws DataException;

	public void setSavepoint() throws DataException;

	public void rollbackToSavepoint() throws DataException;

	@Override
	public void close() throws DataException;

	public void rebuild() throws DataException;

	public boolean getDebug();

	public void setDebug(boolean debugState);

	public void backup(boolean quick) throws DataException;

}
