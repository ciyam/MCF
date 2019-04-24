package org.qora.repository;

import java.util.List;

import org.qora.data.network.PeerData;

public interface NetworkRepository {

	public List<PeerData> getAllPeers() throws DataException;

	public void save(PeerData peerData) throws DataException;

	public int delete(PeerData peerData) throws DataException;

	public int deleteAllPeers() throws DataException;

}
