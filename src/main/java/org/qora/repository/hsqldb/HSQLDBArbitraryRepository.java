package org.qora.repository.hsqldb;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.qora.crypto.Crypto;
import org.qora.data.transaction.ArbitraryTransactionData;
import org.qora.data.transaction.ArbitraryTransactionData.DataType;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.ArbitraryRepository;
import org.qora.repository.DataException;
import org.qora.settings.Settings;
import org.qora.utils.Base58;

public class HSQLDBArbitraryRepository implements ArbitraryRepository {

	protected HSQLDBRepository repository;

	public HSQLDBArbitraryRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	/**
	 * Returns pathname for saving arbitrary transaction data payloads.
	 * <p>
	 * Format: <tt>arbitrary/<sender>/<service><tx-sig>.raw</tt>
	 * 
	 * @param arbitraryTransactionData
	 * @return
	 */
	public static String buildPathname(ArbitraryTransactionData arbitraryTransactionData) {
		String senderAddress = Crypto.toAddress(arbitraryTransactionData.getSenderPublicKey());

		StringBuilder stringBuilder = new StringBuilder(1024);

		stringBuilder.append(Settings.getInstance().getUserPath());
		stringBuilder.append("arbitrary");
		stringBuilder.append(File.separator);
		stringBuilder.append(senderAddress);
		stringBuilder.append(File.separator);
		stringBuilder.append(arbitraryTransactionData.getService());
		stringBuilder.append(File.separator);
		stringBuilder.append(Base58.encode(arbitraryTransactionData.getSignature()));
		stringBuilder.append(".raw");

		return stringBuilder.toString();
	}

	private String buildPathname(byte[] signature) throws DataException {
		TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(signature);
		if (transactionData == null)
			return null;

		return buildPathname((ArbitraryTransactionData) transactionData);
	}

	@Override
	public boolean isDataLocal(byte[] signature) throws DataException {
		String dataPathname = buildPathname(signature);
		if (dataPathname == null)
			return false;

		Path dataPath = Paths.get(dataPathname);
		return Files.exists(dataPath);
	}

	@Override
	public byte[] fetchData(byte[] signature) throws DataException {
		String dataPathname = buildPathname(signature);
		if (dataPathname == null)
			return null;

		Path dataPath = Paths.get(dataPathname);
		try {
			return Files.readAllBytes(dataPath);
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public void save(ArbitraryTransactionData arbitraryTransactionData) throws DataException {
		// Refuse to store raw data in the repository - it needs to be saved elsewhere!
		if (arbitraryTransactionData.getDataType() == DataType.RAW_DATA) {
			byte[] rawData = arbitraryTransactionData.getData();

			// Calculate hash of data and update our transaction to use that
			byte[] dataHash = Crypto.digest(rawData);
			arbitraryTransactionData.setData(dataHash);
			arbitraryTransactionData.setDataType(DataType.DATA_HASH);

			String dataPathname = buildPathname(arbitraryTransactionData);

			Path dataPath = Paths.get(dataPathname);

			// Make sure directory structure exists
			try {
				Files.createDirectories(dataPath.getParent());
			} catch (IOException e) {
				throw new DataException("Unable to create arbitrary transaction directory", e);
			}

			// Output actual transaction data
			try (OutputStream dataOut = Files.newOutputStream(dataPath)) {
				dataOut.write(rawData);
			} catch (IOException e) {
				throw new DataException("Unable to store arbitrary transaction data", e);
			}
		}
	}

	@Override
	public void delete(ArbitraryTransactionData arbitraryTransactionData) throws DataException {
		String dataPathname = buildPathname(arbitraryTransactionData);
		Path dataPath = Paths.get(dataPathname);
		try {
			Files.deleteIfExists(dataPath);

			// Also attempt to delete parent <service> directory if empty
			Path servicePath = dataPath.getParent();
			Files.deleteIfExists(servicePath);

			// Also attempt to delete parent <sender's address> directory if empty
			Path senderpath = servicePath.getParent();
			Files.deleteIfExists(senderpath);
		} catch (DirectoryNotEmptyException e) {
			// One of the parent service/sender directories still has data from other transactions - this is OK
		} catch (IOException e) {
			throw new DataException("Unable to delete arbitrary transaction data", e);
		}
	}

}
