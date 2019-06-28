package org.qora.repository;

public interface Repository extends AutoCloseable {

	public ATRepository getATRepository();

	public AccountRepository getAccountRepository();

	public AssetRepository getAssetRepository();

	public BlockRepository getBlockRepository();

	public GroupRepository getGroupRepository();

	public NameRepository getNameRepository();

	public TransactionRepository getTransactionRepository();

	public VotingRepository getVotingRepository();

	public void saveChanges() throws DataException;

	public void discardChanges() throws DataException;

	@Override
	public void close() throws DataException;

	public void rebuild() throws DataException;

}
