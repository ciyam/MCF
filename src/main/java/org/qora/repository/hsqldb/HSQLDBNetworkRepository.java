package org.qora.repository.hsqldb;

import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.qora.data.network.PeerData;
import org.qora.repository.DataException;
import org.qora.repository.NetworkRepository;

public class HSQLDBNetworkRepository implements NetworkRepository {

	protected HSQLDBRepository repository;

	public HSQLDBNetworkRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<PeerData> getAllPeers() throws DataException {
		List<PeerData> peers = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT hostname, port, last_connected, last_attempted, last_height FROM Peers")) {
			if (resultSet == null)
				return peers;

			// NOTE: do-while because checkedExecute() above has already called rs.next() for us
			do {
				String hostname = resultSet.getString(1);
				int port = resultSet.getInt(2);
				InetSocketAddress socketAddress = InetSocketAddress.createUnresolved(hostname, port);

				Timestamp lastConnectedTimestamp = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC));
				Long lastConnected = resultSet.wasNull() ? null : lastConnectedTimestamp.getTime();

				Timestamp lastAttemptedTimestamp = resultSet.getTimestamp(4, Calendar.getInstance(HSQLDBRepository.UTC));
				Long lastAttempted = resultSet.wasNull() ? null : lastAttemptedTimestamp.getTime();

				Integer lastHeight = resultSet.getInt(5);
				if (resultSet.wasNull())
					lastHeight = null;

				peers.add(new PeerData(socketAddress, lastConnected, lastAttempted, lastHeight));
			} while (resultSet.next());

			return peers;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch poll votes from repository", e);
		}
	}

	@Override
	public void save(PeerData peerData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Peers");

		Timestamp lastConnected = peerData.getLastConnected() == null ? null : new Timestamp(peerData.getLastConnected());
		Timestamp lastAttempted = peerData.getLastAttempted() == null ? null : new Timestamp(peerData.getLastAttempted());

		saveHelper.bind("hostname", peerData.getSocketAddress().getHostString()).bind("port", peerData.getSocketAddress().getPort())
				.bind("last_connected", lastConnected).bind("last_attempted", lastAttempted).bind("last_height", peerData.getLastHeight());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save peer into repository", e);
		}
	}

	@Override
	public int delete(PeerData peerData) throws DataException {
		try {
			return this.repository.delete("Peers", "hostname = ? AND port = ?", peerData.getSocketAddress().getHostString(),
					peerData.getSocketAddress().getPort());
		} catch (SQLException e) {
			throw new DataException("Unable to delete peer from repository", e);
		}
	}

}
