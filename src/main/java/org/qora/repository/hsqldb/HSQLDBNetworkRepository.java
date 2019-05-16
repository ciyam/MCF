package org.qora.repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.qora.data.network.PeerData;
import org.qora.network.PeerAddress;
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

		try (ResultSet resultSet = this.repository.checkedExecute("SELECT address, last_connected, last_attempted, last_height, last_misbehaved FROM Peers")) {
			if (resultSet == null)
				return peers;

			// NOTE: do-while because checkedExecute() above has already called rs.next() for us
			do {
				String address = resultSet.getString(1);
				PeerAddress peerAddress = PeerAddress.fromString(address);

				Timestamp lastConnectedTimestamp = resultSet.getTimestamp(2, Calendar.getInstance(HSQLDBRepository.UTC));
				Long lastConnected = resultSet.wasNull() ? null : lastConnectedTimestamp.getTime();

				Timestamp lastAttemptedTimestamp = resultSet.getTimestamp(3, Calendar.getInstance(HSQLDBRepository.UTC));
				Long lastAttempted = resultSet.wasNull() ? null : lastAttemptedTimestamp.getTime();

				Integer lastHeight = resultSet.getInt(4);
				if (resultSet.wasNull())
					lastHeight = null;

				Timestamp lastMisbehavedTimestamp = resultSet.getTimestamp(5, Calendar.getInstance(HSQLDBRepository.UTC));
				Long lastMisbehaved = resultSet.wasNull() ? null : lastMisbehavedTimestamp.getTime();

				peers.add(new PeerData(peerAddress, lastAttempted, lastConnected, lastHeight, lastMisbehaved));
			} while (resultSet.next());

			return peers;
		} catch (IllegalArgumentException e) {
			throw new DataException("Refusing to fetch invalid peer from repository", e);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch peers from repository", e);
		}
	}

	@Override
	public void save(PeerData peerData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Peers");

		Timestamp lastConnected = peerData.getLastConnected() == null ? null : new Timestamp(peerData.getLastConnected());
		Timestamp lastAttempted = peerData.getLastAttempted() == null ? null : new Timestamp(peerData.getLastAttempted());
		Timestamp lastMisbehaved = peerData.getLastMisbehaved() == null ? null : new Timestamp(peerData.getLastMisbehaved());

		saveHelper.bind("address", peerData.getAddress().toString()).bind("last_connected", lastConnected).bind("last_attempted", lastAttempted)
				.bind("last_height", peerData.getLastHeight()).bind("last_misbehaved", lastMisbehaved);

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save peer into repository", e);
		}
	}

	@Override
	public int delete(PeerData peerData) throws DataException {
		try {
			return this.repository.delete("Peers", "address = ?", peerData.getAddress().toString());
		} catch (SQLException e) {
			throw new DataException("Unable to delete peer from repository", e);
		}
	}

	@Override
	public int deleteAllPeers() throws DataException {
		try {
			return this.repository.delete("Peers");
		} catch (SQLException e) {
			throw new DataException("Unable to delete peers from repository", e);
		}
	}
}
