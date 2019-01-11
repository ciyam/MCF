package org.qora.repository.hsqldb;

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
		connection.commit();
	}

	/**
	 * Fetch current version of database schema.
	 * 
	 * @return int, 0 if no schema yet
	 * @throws SQLException
	 */
	private static int fetchDatabaseVersion(Connection connection) throws SQLException {
		int databaseVersion = 0;

		try (Statement stmt = connection.createStatement()) {
			if (stmt.execute("SELECT version FROM DatabaseInfo"))
				try (ResultSet resultSet = stmt.getResultSet()) {
					if (resultSet.next())
						databaseVersion = resultSet.getInt(1);
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

		try (Statement stmt = connection.createStatement()) {

			/*
			 * Try not to add too many constraints as much of these checks will be performed during transaction validation. Also some constraints might be too
			 * harsh on competing unconfirmed transactions.
			 * 
			 * Only really add "ON DELETE CASCADE" to sub-tables that store type-specific data. For example on sub-types of Transactions like
			 * PaymentTransactions. A counterexample would be adding "ON DELETE CASCADE" to Assets using Assets' "reference" as a foreign key referring to
			 * Transactions' "signature". We want to database to automatically delete complete transaction data (Transactions row and corresponding
			 * PaymentTransactions row), but leave deleting less related table rows (Assets) to the Java logic.
			 */

			switch (databaseVersion) {
				case 0:
					// create from new
					// FYI: "UCC" in HSQLDB means "upper-case comparison", i.e. case-insensitive
					stmt.execute("SET DATABASE SQL NAMES TRUE"); // SQL keywords cannot be used as DB object names, e.g. table names
					stmt.execute("SET DATABASE SQL SYNTAX MYS TRUE"); // Required for our use of INSERT ... ON DUPLICATE KEY UPDATE ... syntax
					stmt.execute("SET DATABASE SQL RESTRICT EXEC TRUE"); // No multiple-statement execute() or DDL/DML executeQuery()
					stmt.execute("SET DATABASE DEFAULT TABLE TYPE CACHED");
					stmt.execute("SET DATABASE COLLATION SQL_TEXT NO PAD"); // Do not pad strings to same length before comparison
					stmt.execute("CREATE COLLATION SQL_TEXT_UCC_NO_PAD FOR SQL_TEXT FROM SQL_TEXT_UCC NO PAD");
					stmt.execute("CREATE COLLATION SQL_TEXT_NO_PAD FOR SQL_TEXT FROM SQL_TEXT NO PAD");
					stmt.execute("SET FILES SPACE TRUE"); // Enable per-table block space within .data file, useful for CACHED table types
					stmt.execute("SET FILES LOB SCALE 1"); // LOB granularity is 1KB
					stmt.execute("CREATE TABLE DatabaseInfo ( version INTEGER NOT NULL )");
					stmt.execute("INSERT INTO DatabaseInfo VALUES ( 0 )");
					stmt.execute("CREATE TYPE BlockSignature AS VARBINARY(128)");
					stmt.execute("CREATE TYPE Signature AS VARBINARY(64)");
					stmt.execute("CREATE TYPE QoraAddress AS VARCHAR(36)");
					stmt.execute("CREATE TYPE QoraPublicKey AS VARBINARY(32)");
					stmt.execute("CREATE TYPE QoraAmount AS DECIMAL(27, 8)");
					stmt.execute("CREATE TYPE GenericDescription AS VARCHAR(4000)");
					stmt.execute("CREATE TYPE RegisteredName AS VARCHAR(400) COLLATE SQL_TEXT_NO_PAD");
					stmt.execute("CREATE TYPE NameData AS VARCHAR(4000)");
					stmt.execute("CREATE TYPE PollName AS VARCHAR(400) COLLATE SQL_TEXT_NO_PAD");
					stmt.execute("CREATE TYPE PollOption AS VARCHAR(400) COLLATE SQL_TEXT_UCC_NO_PAD");
					stmt.execute("CREATE TYPE PollOptionIndex AS INTEGER");
					stmt.execute("CREATE TYPE DataHash AS VARBINARY(32)");
					stmt.execute("CREATE TYPE AssetID AS BIGINT");
					stmt.execute("CREATE TYPE AssetName AS VARCHAR(400) COLLATE SQL_TEXT_NO_PAD");
					stmt.execute("CREATE TYPE AssetOrderID AS VARBINARY(64)");
					stmt.execute("CREATE TYPE ATName AS VARCHAR(200) COLLATE SQL_TEXT_UCC_NO_PAD");
					stmt.execute("CREATE TYPE ATType AS VARCHAR(200) COLLATE SQL_TEXT_UCC_NO_PAD");
					stmt.execute("CREATE TYPE ATCode AS BLOB(64K)"); // 16bit * 1
					stmt.execute("CREATE TYPE ATState AS BLOB(1M)"); // 16bit * 8 + 16bit * 4 + 16bit * 4
					stmt.execute("CREATE TYPE ATStateHash as VARBINARY(32)");
					stmt.execute("CREATE TYPE ATMessage AS VARBINARY(256)");
					stmt.execute("CREATE TYPE GroupName AS VARCHAR(400) COLLATE SQL_TEXT_UCC_NO_PAD");
					break;

				case 1:
					// Blocks
					stmt.execute("CREATE TABLE Blocks (signature BlockSignature, version TINYINT NOT NULL, reference BlockSignature, "
							+ "transaction_count INTEGER NOT NULL, total_fees QoraAmount NOT NULL, transactions_signature Signature NOT NULL, "
							+ "height INTEGER NOT NULL, generation TIMESTAMP WITH TIME ZONE NOT NULL, generating_balance QoraAmount NOT NULL, "
							+ "generator QoraPublicKey NOT NULL, generator_signature Signature NOT NULL, AT_count INTEGER NOT NULL, AT_fees QoraAmount NOT NULL, "
							+ "PRIMARY KEY (signature))");
					// For finding blocks by height.
					stmt.execute("CREATE INDEX BlockHeightIndex ON Blocks (height)");
					// For finding blocks by the account that generated them.
					stmt.execute("CREATE INDEX BlockGeneratorIndex ON Blocks (generator)");
					// For finding blocks by reference, e.g. child blocks.
					stmt.execute("CREATE INDEX BlockReferenceIndex ON Blocks (reference)");
					// Use a separate table space as this table will be very large.
					stmt.execute("SET TABLE Blocks NEW SPACE");
					break;

				case 2:
					// Generic transactions (null reference, creator and milestone_block for genesis transactions)
					stmt.execute("CREATE TABLE Transactions (signature Signature, reference Signature, type TINYINT NOT NULL, "
							+ "creator QoraPublicKey NOT NULL, creation TIMESTAMP WITH TIME ZONE NOT NULL, fee QoraAmount NOT NULL, milestone_block BlockSignature, "
							+ "PRIMARY KEY (signature))");
					// For finding transactions by transaction type.
					stmt.execute("CREATE INDEX TransactionTypeIndex ON Transactions (type)");
					// For finding transactions using creation timestamp.
					stmt.execute("CREATE INDEX TransactionCreationIndex ON Transactions (creation)");
					// For when a user wants to lookup ALL transactions they have created, with optional type.
					stmt.execute("CREATE INDEX TransactionCreatorIndex ON Transactions (creator, type)");
					// For finding transactions by reference, e.g. child transactions.
					stmt.execute("CREATE INDEX TransactionReferenceIndex ON Transactions (reference)");
					// Use a separate table space as this table will be very large.
					stmt.execute("SET TABLE Transactions NEW SPACE");

					// Transaction-Block mapping ("transaction_signature" is unique as a transaction cannot be included in more than one block)
					stmt.execute("CREATE TABLE BlockTransactions (block_signature BlockSignature, sequence INTEGER, transaction_signature Signature UNIQUE, "
							+ "PRIMARY KEY (block_signature, sequence), FOREIGN KEY (transaction_signature) REFERENCES Transactions (signature) ON DELETE CASCADE, "
							+ "FOREIGN KEY (block_signature) REFERENCES Blocks (signature) ON DELETE CASCADE)");
					// Use a separate table space as this table will be very large.
					stmt.execute("SET TABLE BlockTransactions NEW SPACE");

					// Unconfirmed transactions
					// We use this as searching for transactions with no corresponding mapping in BlockTransactions is much slower.
					stmt.execute("CREATE TABLE UnconfirmedTransactions (signature Signature PRIMARY KEY, creation TIMESTAMP WITH TIME ZONE NOT NULL)");
					// Index to allow quick sorting by creation-else-signature
					stmt.execute("CREATE INDEX UnconfirmedTransactionsIndex ON UnconfirmedTransactions (creation, signature)");

					// Transaction participants
					// To allow lookup of all activity by an address
					stmt.execute("CREATE TABLE TransactionParticipants (signature Signature, participant QoraAddress NOT NULL, "
							+ "FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// Use a separate table space as this table will be very large.
					stmt.execute("SET TABLE TransactionParticipants NEW SPACE");
					break;

				case 3:
					// Genesis Transactions
					stmt.execute("CREATE TABLE GenesisTransactions (signature Signature, recipient QoraAddress NOT NULL, "
							+ "amount QoraAmount NOT NULL, asset_id AssetID NOT NULL, PRIMARY KEY (signature), "
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
							+ "new_owner QoraAddress NOT NULL, new_data NameData NOT NULL, name_reference Signature, "
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
							+ "seller QoraAddress NOT NULL, amount QoraAmount NOT NULL, name_reference Signature, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					break;

				case 10:
					// Create Poll Transactions
					stmt.execute("CREATE TABLE CreatePollTransactions (signature Signature, creator QoraPublicKey NOT NULL, owner QoraAddress NOT NULL, "
							+ "poll_name PollName NOT NULL, description GenericDescription NOT NULL, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// Poll options. NB: option is implicitly NON NULL and UNIQUE due to being part of compound primary key
					stmt.execute("CREATE TABLE CreatePollTransactionOptions (signature Signature, option_index TINYINT NOT NULL, option_name PollOption, "
							+ "PRIMARY KEY (signature, option_index), FOREIGN KEY (signature) REFERENCES CreatePollTransactions (signature) ON DELETE CASCADE)");
					// For the future: add flag to polls to allow one or multiple votes per voter
					break;

				case 11:
					// Vote On Poll Transactions
					stmt.execute("CREATE TABLE VoteOnPollTransactions (signature Signature, voter QoraPublicKey NOT NULL, poll_name PollName NOT NULL, "
							+ "option_index PollOptionIndex NOT NULL, previous_option_index PollOptionIndex, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					break;

				case 12:
					// Arbitrary/Multi-payment/Message/Payment Transaction Payments
					stmt.execute("CREATE TABLE SharedTransactionPayments (signature Signature, recipient QoraAddress NOT NULL, "
							+ "amount QoraAmount NOT NULL, asset_id AssetID NOT NULL, "
							+ "PRIMARY KEY (signature, recipient, asset_id), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					break;

				case 13:
					// Arbitrary Transactions
					stmt.execute("CREATE TABLE ArbitraryTransactions (signature Signature, sender QoraPublicKey NOT NULL, version TINYINT NOT NULL, "
							+ "service TINYINT NOT NULL, data_hash DataHash NOT NULL, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// NB: Actual data payload stored elsewhere
					// For the future: data payload should be encrypted, at the very least with transaction's reference as the seed for the encryption key
					break;

				case 14:
					// Issue Asset Transactions
					stmt.execute(
							"CREATE TABLE IssueAssetTransactions (signature Signature, issuer QoraPublicKey NOT NULL, owner QoraAddress NOT NULL, asset_name AssetName NOT NULL, "
									+ "description GenericDescription NOT NULL, quantity BIGINT NOT NULL, is_divisible BOOLEAN NOT NULL, asset_id AssetID, "
									+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// For the future: maybe convert quantity from BIGINT to QoraAmount, regardless of divisibility
					break;

				case 15:
					// Transfer Asset Transactions
					stmt.execute("CREATE TABLE TransferAssetTransactions (signature Signature, sender QoraPublicKey NOT NULL, recipient QoraAddress NOT NULL, "
							+ "asset_id AssetID NOT NULL, amount QoraAmount NOT NULL,"
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
							+ "asset_order_id AssetOrderID NOT NULL, "
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
							+ "creation_bytes VARBINARY(100000) NOT NULL, amount QoraAmount NOT NULL, asset_id AssetID NOT NULL, AT_address QoraAddress, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// For looking up the Deploy AT Transaction based on deployed AT address
					stmt.execute("CREATE INDEX DeployATAddressIndex on DeployATTransactions (AT_address)");
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
					stmt.execute("CREATE TABLE Assets (asset_id AssetID, owner QoraAddress NOT NULL, "
							+ "asset_name AssetName NOT NULL, description GenericDescription NOT NULL, "
							+ "quantity BIGINT NOT NULL, is_divisible BOOLEAN NOT NULL, reference Signature NOT NULL, PRIMARY KEY (asset_id))");
					// We need a corresponding trigger to make sure new asset_id values are assigned sequentially
					stmt.execute(
							"CREATE TRIGGER Asset_ID_Trigger BEFORE INSERT ON Assets REFERENCING NEW ROW AS new_row FOR EACH ROW WHEN (new_row.asset_id IS NULL) "
									+ "SET new_row.asset_id = (SELECT IFNULL(MAX(asset_id) + 1, 0) FROM Assets)");
					// For when a user wants to lookup an asset by name
					stmt.execute("CREATE INDEX AssetNameIndex on Assets (asset_name)");
					break;

				case 22:
					// Accounts
					stmt.execute("CREATE TABLE Accounts (account QoraAddress, reference Signature, public_key QoraPublicKey, PRIMARY KEY (account))");
					stmt.execute("CREATE TABLE AccountBalances (account QoraAddress, asset_id AssetID, balance QoraAmount NOT NULL, "
							+ "PRIMARY KEY (account, asset_id), FOREIGN KEY (account) REFERENCES Accounts (account) ON DELETE CASCADE)");
					// For looking up an account by public key
					stmt.execute("CREATE INDEX AccountPublicKeyIndex on Accounts (public_key)");
					break;

				case 23:
					// Asset Orders
					stmt.execute(
							"CREATE TABLE AssetOrders (asset_order_id AssetOrderID, creator QoraPublicKey NOT NULL, have_asset_id AssetID NOT NULL, want_asset_id AssetID NOT NULL, "
									+ "amount QoraAmount NOT NULL, fulfilled QoraAmount NOT NULL, price QoraAmount NOT NULL, "
									+ "ordered TIMESTAMP WITH TIME ZONE NOT NULL, is_closed BOOLEAN NOT NULL, is_fulfilled BOOLEAN NOT NULL, "
									+ "PRIMARY KEY (asset_order_id))");
					// For quick matching of orders. is_closed are is_fulfilled included so inactive orders can be filtered out.
					stmt.execute("CREATE INDEX AssetOrderMatchingIndex on AssetOrders (have_asset_id, want_asset_id, is_closed, is_fulfilled, price, ordered)");
					// For when a user wants to look up their current/historic orders. is_closed included so user can filter by active/inactive orders.
					stmt.execute("CREATE INDEX AssetOrderCreatorIndex on AssetOrders (creator, is_closed)");
					break;

				case 24:
					// Asset Trades
					stmt.execute("CREATE TABLE AssetTrades (initiating_order_id AssetOrderId NOT NULL, target_order_id AssetOrderId NOT NULL, "
							+ "amount QoraAmount NOT NULL, price QoraAmount NOT NULL, traded TIMESTAMP WITH TIME ZONE NOT NULL)");
					// For looking up historic trades based on orders
					stmt.execute("CREATE INDEX AssetTradeBuyOrderIndex on AssetTrades (initiating_order_id, traded)");
					stmt.execute("CREATE INDEX AssetTradeSellOrderIndex on AssetTrades (target_order_id, traded)");
					break;

				case 25:
					// Polls/Voting
					stmt.execute(
							"CREATE TABLE Polls (poll_name PollName, description GenericDescription NOT NULL, creator QoraPublicKey NOT NULL, owner QoraAddress NOT NULL, "
									+ "published TIMESTAMP WITH TIME ZONE NOT NULL, " + "PRIMARY KEY (poll_name))");
					// Various options available on a poll
					stmt.execute("CREATE TABLE PollOptions (poll_name PollName, option_index TINYINT NOT NULL, option_name PollOption, "
							+ "PRIMARY KEY (poll_name, option_index), FOREIGN KEY (poll_name) REFERENCES Polls (poll_name) ON DELETE CASCADE)");
					// Actual votes cast on a poll by voting users. NOTE: only one vote per user supported at this time.
					stmt.execute("CREATE TABLE PollVotes (poll_name PollName, voter QoraPublicKey, option_index PollOptionIndex NOT NULL, "
							+ "PRIMARY KEY (poll_name, voter), FOREIGN KEY (poll_name) REFERENCES Polls (poll_name) ON DELETE CASCADE)");
					// For when a user wants to lookup poll they own
					stmt.execute("CREATE INDEX PollOwnerIndex on Polls (owner)");
					break;

				case 26:
					// Registered Names
					stmt.execute(
							"CREATE TABLE Names (name RegisteredName, data VARCHAR(4000) NOT NULL, owner QoraAddress NOT NULL, "
									+ "registered TIMESTAMP WITH TIME ZONE NOT NULL, updated TIMESTAMP WITH TIME ZONE, reference Signature, is_for_sale BOOLEAN NOT NULL, sale_price QoraAmount, "
									+ "PRIMARY KEY (name))");
					break;

				case 27:
					// CIYAM Automated Transactions
					stmt.execute(
							"CREATE TABLE ATs (AT_address QoraAddress, creator QoraPublicKey, creation TIMESTAMP WITH TIME ZONE, version INTEGER NOT NULL, "
									+ "asset_id AssetID NOT NULL, code_bytes ATCode NOT NULL, is_sleeping BOOLEAN NOT NULL, sleep_until_height INTEGER, "
									+ "is_finished BOOLEAN NOT NULL, had_fatal_error BOOLEAN NOT NULL, is_frozen BOOLEAN NOT NULL, frozen_balance QoraAmount, "
									+ "PRIMARY key (AT_address))");
					// For finding executable ATs, ordered by creation timestamp
					stmt.execute("CREATE INDEX ATIndex on ATs (is_finished, creation)");
					// For finding ATs by creator
					stmt.execute("CREATE INDEX ATCreatorIndex on ATs (creator)");

					// AT state on a per-block basis
					stmt.execute("CREATE TABLE ATStates (AT_address QoraAddress, height INTEGER NOT NULL, creation TIMESTAMP WITH TIME ZONE, "
							+ "state_data ATState, state_hash ATStateHash NOT NULL, fees QoraAmount NOT NULL, "
							+ "PRIMARY KEY (AT_address, height), FOREIGN KEY (AT_address) REFERENCES ATs (AT_address) ON DELETE CASCADE)");
					// For finding per-block AT states, ordered by creation timestamp
					stmt.execute("CREATE INDEX BlockATStateIndex on ATStates (height, creation)");

					// Generated AT Transactions
					stmt.execute(
							"CREATE TABLE ATTransactions (signature Signature, AT_address QoraAddress NOT NULL, recipient QoraAddress, amount QoraAmount, asset_id AssetID, message ATMessage, "
									+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// For finding AT Transactions generated by a specific AT
					stmt.execute("CREATE INDEX ATTransactionsIndex on ATTransactions (AT_address)");
					break;

				case 28:
					// XXX TEMP fixes to registered names - remove before database rebuild!
					// Allow name_reference to be NULL while transaction is unconfirmed
					stmt.execute("ALTER TABLE UpdateNameTransactions ALTER COLUMN name_reference SET NULL");
					stmt.execute("ALTER TABLE BuyNameTransactions ALTER COLUMN name_reference SET NULL");
					// Names.registrant shouldn't be there
					stmt.execute("ALTER TABLE Names DROP COLUMN registrant");
					break;

				case 29:
					// XXX TEMP bridging statements for AccountGroups - remove before database rebuild!
					stmt.execute("CREATE TYPE GenericDescription AS VARCHAR(4000)");
					stmt.execute("CREATE TYPE GroupName AS VARCHAR(400) COLLATE SQL_TEXT_UCC_NO_PAD");
					break;

				case 30:
					// Account groups
					stmt.execute("CREATE TABLE AccountGroups (group_name GroupName, owner QoraAddress NOT NULL, description GenericDescription NOT NULL, "
							+ "created TIMESTAMP WITH TIME ZONE NOT NULL, updated TIMESTAMP WITH TIME ZONE, is_open BOOLEAN NOT NULL, "
							+ "reference Signature, PRIMARY KEY (group_name))");
					// For finding groups by owner
					stmt.execute("CREATE INDEX AccountGroupOwnerIndex on AccountGroups (owner)");

					// Admins
					stmt.execute("CREATE TABLE AccountGroupAdmins (group_name GroupName, admin QoraAddress, group_reference Signature NOT NULL, PRIMARY KEY (group_name, admin))");
					// For finding groups that address administrates
					stmt.execute("CREATE INDEX AccountGroupAdminIndex on AccountGroupAdmins (admin)");

					// Members
					stmt.execute("CREATE TABLE AccountGroupMembers (group_name GroupName, address QoraAddress, joined TIMESTAMP WITH TIME ZONE NOT NULL, group_reference Signature NOT NULL, "
							+ "PRIMARY KEY (group_name, address))");
					// For finding groups that address is member
					stmt.execute("CREATE INDEX AccountGroupMemberIndex on AccountGroupMembers (address)");

					// Invites
					// PRIMARY KEY (invitee + group + inviter) because most queries will be "what have I been invited to?" from UI
					stmt.execute("CREATE TABLE AccountGroupInvites (group_name GroupName, invitee QoraAddress, inviter QoraAddress, "
							+ "expiry TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY (invitee, group_name, inviter))");
					// For finding invites sent by inviter
					stmt.execute("CREATE INDEX AccountGroupSentInviteIndex on AccountGroupInvites (inviter)");
					// For finding invites by group
					stmt.execute("CREATE INDEX AccountGroupInviteIndex on AccountGroupInvites (group_name)");

					// Bans
					// NULL expiry means does not expire!
					stmt.execute("CREATE TABLE AccountGroupBans (group_name GroupName, offender QoraAddress, admin QoraAddress NOT NULL, banned TIMESTAMP WITH TIME ZONE NOT NULL, "
							+ "reason GenericDescription NOT NULL, expiry TIMESTAMP WITH TIME ZONE, PRIMARY KEY (group_name, offender))");
					// For expiry maintenance
					stmt.execute("CREATE INDEX AccountGroupBanExpiryIndex on AccountGroupBans (expiry)");
					break;

				case 31:
					// Account group transactions
					stmt.execute("CREATE TABLE CreateGroupTransactions (signature Signature, creator QoraPublicKey NOT NULL, group_name GroupName NOT NULL, "
							+ "owner QoraAddress NOT NULL, description GenericDescription NOT NULL, is_open BOOLEAN NOT NULL, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					stmt.execute("CREATE TABLE UpdateGroupTransactions (signature Signature, owner QoraPublicKey NOT NULL, group_name GroupName NOT NULL, "
							+ "new_owner QoraAddress NOT NULL, new_description GenericDescription NOT NULL, new_is_open BOOLEAN NOT NULL, group_reference Signature, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");

					// Account group join/leave transactions
					stmt.execute("CREATE TABLE JoinGroupTransactions (signature Signature, joiner QoraPublicKey NOT NULL, group_name GroupName NOT NULL, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					stmt.execute("CREATE TABLE LeaveGroupTransactions (signature Signature, leaver QoraPublicKey NOT NULL, group_name GroupName NOT NULL, "
							+ "member_reference Signature, admin_reference Signature, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					break;

				default:
					// nothing to do
					return false;
			}
		}

		// database was updated
		return true;
	}

}
