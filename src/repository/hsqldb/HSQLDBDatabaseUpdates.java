package repository.hsqldb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class HSQLDBDatabaseUpdates {

	/**
	 * Apply any incremental changes to database schema.
	 * 
	 * @throws SQLException
	 */
	public static void updateDatabase(Connection connection) throws SQLException {
		while (databaseUpdating(connection))
			incrementDatabaseVersion(connection);
	}

	/**
	 * Increment database's schema version.
	 * 
	 * @throws SQLException
	 */
	private static void incrementDatabaseVersion(Connection connection) throws SQLException {
		connection.createStatement().execute("UPDATE DatabaseInfo SET version = version + 1");
	}

	/**
	 * Fetch current version of database schema.
	 * 
	 * @return int, 0 if no schema yet
	 * @throws SQLException
	 */
	private static int fetchDatabaseVersion(Connection connection) throws SQLException {
		int databaseVersion = 0;

		try {
			Statement stmt = connection.createStatement();
			if (stmt.execute("SELECT version FROM DatabaseInfo")) {
				ResultSet rs = stmt.getResultSet();

				if (rs.next())
					databaseVersion = rs.getInt(1);
			}
		} catch (SQLException e) {
			// empty database
		}

		return databaseVersion;
	}

	/**
	 * Incrementally update database schema, returning whether an update happened.
	 * 
	 * @return true - if a schema update happened, false otherwise
	 * @throws SQLException
	 */
	private static boolean databaseUpdating(Connection connection) throws SQLException {
		int databaseVersion = fetchDatabaseVersion(connection);

		Statement stmt = connection.createStatement();

		/*
		 * Try not to add too many constraints as much of these checks will be performed during transaction validation. Also some constraints might be too harsh
		 * on competing unconfirmed transactions.
		 * 
		 * Only really add "ON DELETE CASCADE" to sub-tables that store type-specific data. For example on sub-types of Transactions like PaymentTransactions. A
		 * counterexample would be adding "ON DELETE CASCADE" to Assets using Assets' "reference" as a foreign key referring to Transactions' "signature". We
		 * want to database to automatically delete complete transaction data (Transactions row and corresponding PaymentTransactions row), but leave deleting
		 * less related table rows (Assets) to the Java logic.
		 */

		switch (databaseVersion) {
			case 0:
				// create from new
				stmt.execute("SET DATABASE DEFAULT TABLE TYPE CACHED");
				stmt.execute("SET FILES SPACE TRUE");
				stmt.execute("CREATE TABLE DatabaseInfo ( version INTEGER NOT NULL )");
				stmt.execute("INSERT INTO DatabaseInfo VALUES ( 0 )");
				stmt.execute("CREATE TYPE BlockSignature AS VARBINARY(128)");
				stmt.execute("CREATE TYPE Signature AS VARBINARY(64)");
				stmt.execute("CREATE TYPE QoraAddress AS VARCHAR(36)");
				stmt.execute("CREATE TYPE QoraPublicKey AS VARBINARY(32)");
				stmt.execute("CREATE TYPE QoraAmount AS DECIMAL(19, 8)");
				stmt.execute("CREATE TYPE RegisteredName AS VARCHAR(400) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE TYPE NameData AS VARCHAR(4000)");
				stmt.execute("CREATE TYPE PollName AS VARCHAR(400) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE TYPE PollOption AS VARCHAR(400) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE TYPE DataHash AS VARCHAR(100)");
				stmt.execute("CREATE TYPE AssetID AS BIGINT");
				stmt.execute("CREATE TYPE AssetName AS VARCHAR(400) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE TYPE AssetOrderID AS VARCHAR(100)");
				stmt.execute("CREATE TYPE ATName AS VARCHAR(200) COLLATE SQL_TEXT_UCC");
				stmt.execute("CREATE TYPE ATType AS VARCHAR(200) COLLATE SQL_TEXT_UCC");
				break;

			case 1:
				// Blocks
				stmt.execute("CREATE TABLE Blocks (signature BlockSignature PRIMARY KEY, version TINYINT NOT NULL, reference BlockSignature, "
						+ "transaction_count INTEGER NOT NULL, total_fees QoraAmount NOT NULL, transactions_signature Signature NOT NULL, "
						+ "height INTEGER NOT NULL, generation TIMESTAMP NOT NULL, generating_balance QoraAmount NOT NULL, "
						+ "generator QoraPublicKey NOT NULL, generator_signature Signature NOT NULL, AT_data VARBINARY(20000), AT_fees QoraAmount)");
				stmt.execute("CREATE INDEX BlockHeightIndex ON Blocks (height)");
				stmt.execute("CREATE INDEX BlockGeneratorIndex ON Blocks (generator)");
				stmt.execute("CREATE INDEX BlockReferenceIndex ON Blocks (reference)");
				stmt.execute("SET TABLE Blocks NEW SPACE");
				break;

			case 2:
				// Generic transactions (null reference, creator and milestone_block for genesis transactions)
				stmt.execute("CREATE TABLE Transactions (signature Signature PRIMARY KEY, reference Signature, type TINYINT NOT NULL, "
						+ "creator QoraPublicKey, creation TIMESTAMP NOT NULL, fee QoraAmount NOT NULL, milestone_block BlockSignature)");
				stmt.execute("CREATE INDEX TransactionTypeIndex ON Transactions (type)");
				stmt.execute("CREATE INDEX TransactionCreationIndex ON Transactions (creation)");
				stmt.execute("CREATE INDEX TransactionCreatorIndex ON Transactions (creator)");
				stmt.execute("CREATE INDEX TransactionReferenceIndex ON Transactions (reference)");
				stmt.execute("SET TABLE Transactions NEW SPACE");

				// Transaction-Block mapping ("signature" is unique as a transaction cannot be included in more than one block)
				stmt.execute("CREATE TABLE BlockTransactions (block_signature BlockSignature, sequence INTEGER, transaction_signature Signature, "
						+ "PRIMARY KEY (block_signature, sequence), FOREIGN KEY (transaction_signature) REFERENCES Transactions (signature) ON DELETE CASCADE, "
						+ "FOREIGN KEY (block_signature) REFERENCES Blocks (signature) ON DELETE CASCADE)");
				stmt.execute("SET TABLE BlockTransactions NEW SPACE");

				// Unconfirmed transactions
				// Do we need this? If a transaction doesn't have a corresponding BlockTransactions record then it's unconfirmed?
				stmt.execute("CREATE TABLE UnconfirmedTransactions (signature Signature PRIMARY KEY, expiry TIMESTAMP NOT NULL)");
				stmt.execute("CREATE INDEX UnconfirmedTransactionExpiryIndex ON UnconfirmedTransactions (expiry)");

				// Transaction recipients
				stmt.execute("CREATE TABLE TransactionRecipients (signature Signature, recipient QoraAddress NOT NULL, "
						+ "FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				stmt.execute("SET TABLE TransactionRecipients NEW SPACE");
				break;

			case 3:
				// Genesis Transactions
				stmt.execute("CREATE TABLE GenesisTransactions (signature Signature, recipient QoraAddress NOT NULL, "
						+ "amount QoraAmount NOT NULL, PRIMARY KEY (signature), "
						+ "FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 4:
				// Payment Transactions
				stmt.execute("CREATE TABLE PaymentTransactions (signature Signature, sender QoraPublicKey NOT NULL, recipient QoraAddress NOT NULL, "
						+ "amount QoraAmount NOT NULL, PRIMARY KEY (signature), "
						+ "FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 5:
				// Register Name Transactions
				stmt.execute("CREATE TABLE RegisterNameTransactions (signature Signature, registrant QoraPublicKey NOT NULL, name RegisteredName NOT NULL, "
						+ "owner QoraAddress NOT NULL, data NameData NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 6:
				// Update Name Transactions
				stmt.execute("CREATE TABLE UpdateNameTransactions (signature Signature, owner QoraPublicKey NOT NULL, name RegisteredName NOT NULL, "
						+ "new_owner QoraAddress NOT NULL, new_data NameData NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 7:
				// Sell Name Transactions
				stmt.execute("CREATE TABLE SellNameTransactions (signature Signature, owner QoraPublicKey NOT NULL, name RegisteredName NOT NULL, "
						+ "amount QoraAmount NOT NULL, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 8:
				// Cancel Sell Name Transactions
				stmt.execute("CREATE TABLE CancelSellNameTransactions (signature Signature, owner QoraPublicKey NOT NULL, name RegisteredName NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 9:
				// Buy Name Transactions
				stmt.execute("CREATE TABLE BuyNameTransactions (signature Signature, buyer QoraPublicKey NOT NULL, name RegisteredName NOT NULL, "
						+ "seller QoraAddress NOT NULL, amount QoraAmount NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 10:
				// Create Poll Transactions
				stmt.execute("CREATE TABLE CreatePollTransactions (signature Signature, creator QoraPublicKey NOT NULL, poll PollName NOT NULL, "
						+ "description VARCHAR(4000) NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				// Poll options. NB: option is implicitly NON NULL and UNIQUE due to being part of compound primary key
				stmt.execute("CREATE TABLE CreatePollTransactionOptions (signature Signature, option PollOption, "
						+ "PRIMARY KEY (signature, option), FOREIGN KEY (signature) REFERENCES CreatePollTransactions (signature) ON DELETE CASCADE)");
				// For the future: add flag to polls to allow one or multiple votes per voter
				break;

			case 11:
				// Vote On Poll Transactions
				stmt.execute("CREATE TABLE VoteOnPollTransactions (signature Signature, voter QoraPublicKey NOT NULL, poll PollName NOT NULL, "
						+ "option_index INTEGER NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 12:
				// Arbitrary/Multi-payment Transaction Payments
				stmt.execute("CREATE TABLE SharedTransactionPayments (signature Signature, recipient QoraPublicKey NOT NULL, "
						+ "amount QoraAmount NOT NULL, asset_id AssetID NOT NULL, "
						+ "PRIMARY KEY (signature, recipient, asset_id), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 13:
				// Arbitrary Transactions
				stmt.execute("CREATE TABLE ArbitraryTransactions (signature Signature, creator QoraPublicKey NOT NULL, service TINYINT NOT NULL, "
						+ "data_hash DataHash NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				// NB: Actual data payload stored elsewhere
				// For the future: data payload should be encrypted, at the very least with transaction's reference as the seed for the encryption key
				break;

			case 14:
				// Issue Asset Transactions
				stmt.execute(
						"CREATE TABLE IssueAssetTransactions (signature Signature, issuer QoraPublicKey NOT NULL, owner QoraAddress NOT NULL, asset_name AssetName NOT NULL, "
								+ "description VARCHAR(4000) NOT NULL, quantity BIGINT NOT NULL, is_divisible BOOLEAN NOT NULL, asset_id AssetID, "
								+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				// For the future: maybe convert quantity from BIGINT to QoraAmount, regardless of divisibility
				break;

			case 15:
				// Transfer Asset Transactions
				stmt.execute("CREATE TABLE TransferAssetTransactions (signature Signature, sender QoraPublicKey NOT NULL, recipient QoraAddress NOT NULL, "
						+ "asset_id AssetID NOT NULL, amount QoraAmount NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 16:
				// Create Asset Order Transactions
				stmt.execute("CREATE TABLE CreateAssetOrderTransactions (signature Signature, creator QoraPublicKey NOT NULL, "
						+ "have_asset_id AssetID NOT NULL, amount QoraAmount NOT NULL, want_asset_id AssetID NOT NULL, price QoraAmount NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 17:
				// Cancel Asset Order Transactions
				stmt.execute("CREATE TABLE CancelAssetOrderTransactions (signature Signature, creator QoraPublicKey NOT NULL, "
						+ "asset_order AssetOrderID NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 18:
				// Multi-payment Transactions
				stmt.execute("CREATE TABLE MultiPaymentTransactions (signature Signature, sender QoraPublicKey NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 19:
				// Deploy CIYAM AT Transactions
				stmt.execute("CREATE TABLE DeployATTransactions (signature Signature, creator QoraPublicKey NOT NULL, AT_name ATName NOT NULL, "
						+ "description VARCHAR(2000) NOT NULL, AT_type ATType NOT NULL, AT_tags VARCHAR(200) NOT NULL, "
						+ "creation_bytes VARBINARY(100000) NOT NULL, amount QoraAmount NOT NULL, "
						+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 20:
				// Message Transactions
				stmt.execute(
						"CREATE TABLE MessageTransactions (signature Signature, version TINYINT NOT NULL, sender QoraPublicKey NOT NULL, recipient QoraAddress NOT NULL, "
								+ "is_text BOOLEAN NOT NULL, is_encrypted BOOLEAN NOT NULL, amount QoraAmount NOT NULL, asset_id AssetID NOT NULL, data VARBINARY(4000) NOT NULL, "
								+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
				break;

			case 21:
				// Assets (including QORA coin itself)
				stmt.execute(
						"CREATE TABLE Assets (asset_id AssetID IDENTITY, owner QoraPublicKey NOT NULL, asset_name AssetName NOT NULL, description VARCHAR(4000) NOT NULL, "
								+ "quantity BIGINT NOT NULL, is_divisible BOOLEAN NOT NULL, reference Signature NOT NULL)");
				stmt.execute("CREATE INDEX AssetNameIndex on Assets (asset_name)");
				break;

			case 22:
				// Accounts
				stmt.execute("CREATE TABLE Accounts (account QoraAddress, reference Signature, PRIMARY KEY (account))");
				stmt.execute(
						"CREATE TABLE AccountBalances (account QoraAddress, asset_id AssetID, balance QoraAmount NOT NULL, PRIMARY KEY (account, asset_id))");
				break;

			default:
				// nothing to do
				return false;
		}

		// database was updated
		return true;
	}

}
