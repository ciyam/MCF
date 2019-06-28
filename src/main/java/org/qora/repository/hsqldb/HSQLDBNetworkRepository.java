package org.qora.repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
		String sql = "SELECT address, last_connected, last_attempted, last_misbehaved, added_when, added_by FROM Peers";

		List<PeerData> peers = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return peers;

			// NOTE: do-while because checkedExecute() above has already called rs.next() for us
			do {
				String address = resultSet.getString(1);
				PeerAddress peerAddress = PeerAddress.fromString(address);

				Long lastConnected = HSQLDBRepository.getZonedTimestampMilli(resultSet, 2);

				Long lastAttempted = HSQLDBRepository.getZonedTimestampMilli(resultSet, 3);

				Long lastMisbehaved = HSQLDBRepository.getZonedTimestampMilli(resultSet, 4);

				Long addedWhen = HSQLDBRepository.getZonedTimestampMilli(resultSet, 5);

				String addedBy = resultSet.getString(6);

				peers.add(new PeerData(peerAddress, lastAttempted, lastConnected, lastMisbehaved, addedWhen, addedBy));
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

		saveHelper.bind("address", peerData.getAddress().toString()).bind("last_connected", HSQLDBRepository.toOffsetDateTime(peerData.getLastConnected()))
				.bind("last_attempted", HSQLDBRepository.toOffsetDateTime(peerData.getLastAttempted()))
				.bind("last_misbehaved", HSQLDBRepository.toOffsetDateTime(peerData.getLastMisbehaved()))
				.bind("added_when", HSQLDBRepository.toOffsetDateTime(peerData.getAddedWhen()))
				.bind("added_by", peerData.getAddedBy());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save peer into repository", e);
		}
	}

	@Override
	public int delete(PeerAddress peerAddress) throws DataException {
		try {
			return this.repository.delete("Peers", "address = ?", peerAddress.toString());
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
