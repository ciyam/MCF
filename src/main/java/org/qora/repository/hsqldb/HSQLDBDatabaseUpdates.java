package org.qora.repository.hsqldb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qora.block.BlockChain;

public class HSQLDBDatabaseUpdates {

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBDatabaseUpdates.class);

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
					stmt.execute("SET DATABASE TRANSACTION CONTROL MVCC"); // Use MVCC over default two-phase locking, a-k-a "LOCKS"
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
					stmt.execute("CREATE TYPE RegisteredName AS VARCHAR(128) COLLATE SQL_TEXT_NO_PAD");
					stmt.execute("CREATE TYPE NameData AS VARCHAR(4000)");
					stmt.execute("CREATE TYPE MessageData AS VARBINARY(4000)");
					stmt.execute("CREATE TYPE PollName AS VARCHAR(128) COLLATE SQL_TEXT_NO_PAD");
					stmt.execute("CREATE TYPE PollOption AS VARCHAR(80) COLLATE SQL_TEXT_UCC_NO_PAD");
					stmt.execute("CREATE TYPE PollOptionIndex AS INTEGER");
					stmt.execute("CREATE TYPE DataHash AS VARBINARY(32)");
					stmt.execute("CREATE TYPE AssetID AS BIGINT");
					stmt.execute("CREATE TYPE AssetName AS VARCHAR(34) COLLATE SQL_TEXT_NO_PAD");
					stmt.execute("CREATE TYPE AssetOrderID AS VARBINARY(64)");
					stmt.execute("CREATE TYPE ATName AS VARCHAR(32) COLLATE SQL_TEXT_UCC_NO_PAD");
					stmt.execute("CREATE TYPE ATType AS VARCHAR(32) COLLATE SQL_TEXT_UCC_NO_PAD");
					stmt.execute("CREATE TYPE ATTags AS VARCHAR(80) COLLATE SQL_TEXT_UCC_NO_PAD");
					stmt.execute("CREATE TYPE ATCode AS BLOB(64K)"); // 16bit * 1
					stmt.execute("CREATE TYPE ATState AS BLOB(1M)"); // 16bit * 8 + 16bit * 4 + 16bit * 4
					stmt.execute("CREATE TYPE ATCreationBytes AS BLOB(576K)"); // 16bit * 1 + 16bit * 8
					stmt.execute("CREATE TYPE ATStateHash as VARBINARY(32)");
					stmt.execute("CREATE TYPE ATMessage AS VARBINARY(256)");
					stmt.execute("CREATE TYPE GroupID AS INTEGER");
					stmt.execute("CREATE TYPE GroupName AS VARCHAR(400) COLLATE SQL_TEXT_UCC_NO_PAD");
					stmt.execute("CREATE TYPE GroupReason AS VARCHAR(128) COLLATE SQL_TEXT_UCC_NO_PAD");
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
					// For finding blocks by generation timestamp or finding height of latest block immediately before generation timestamp, etc.
					stmt.execute("CREATE INDEX BlockGenerationHeightIndex ON Blocks (generation, height)");
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
							+ "description GenericDescription NOT NULL, AT_type ATType NOT NULL, AT_tags ATTags NOT NULL, "
							+ "creation_bytes ATCreationBytes NOT NULL, amount QoraAmount NOT NULL, asset_id AssetID NOT NULL, AT_address QoraAddress, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// For looking up the Deploy AT Transaction based on deployed AT address
					stmt.execute("CREATE INDEX DeployATAddressIndex on DeployATTransactions (AT_address)");
					break;

				case 20:
					// Message Transactions
					stmt.execute(
							"CREATE TABLE MessageTransactions (signature Signature, version TINYINT NOT NULL, sender QoraPublicKey NOT NULL, recipient QoraAddress NOT NULL, "
									+ "is_text BOOLEAN NOT NULL, is_encrypted BOOLEAN NOT NULL, amount QoraAmount NOT NULL, asset_id AssetID NOT NULL, data MessageData NOT NULL, "
									+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					break;

				case 21:
					// Assets (including QORA coin itself)
					stmt.execute("CREATE TABLE Assets (asset_id AssetID, owner QoraAddress NOT NULL, "
							+ "asset_name AssetName NOT NULL, description GenericDescription NOT NULL, "
							+ "quantity BIGINT NOT NULL, is_divisible BOOLEAN NOT NULL, reference Signature NOT NULL, PRIMARY KEY (asset_id))");
					// We need a corresponding trigger to make sure new asset_id values are assigned sequentially start from 0
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
					stmt.execute("CREATE TABLE Names (name RegisteredName, data NameData NOT NULL, owner QoraAddress NOT NULL, "
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
					// Account groups
					stmt.execute(
							"CREATE TABLE Groups (group_id GroupID, owner QoraAddress NOT NULL, group_name GroupName, description GenericDescription NOT NULL, "
									+ "created TIMESTAMP WITH TIME ZONE NOT NULL, updated TIMESTAMP WITH TIME ZONE, is_open BOOLEAN NOT NULL, "
									+ "reference Signature, PRIMARY KEY (group_id))");
					// We need a corresponding trigger to make sure new group_id values are assigned sequentially starting from 1
					stmt.execute(
							"CREATE TRIGGER Group_ID_Trigger BEFORE INSERT ON Groups REFERENCING NEW ROW AS new_row FOR EACH ROW WHEN (new_row.group_id IS NULL) "
									+ "SET new_row.group_id = (SELECT IFNULL(MAX(group_id) + 1, 1) FROM Groups)");
					// For when a user wants to lookup an group by name
					stmt.execute("CREATE INDEX GroupNameIndex on Groups (group_name)");
					// For finding groups by owner
					stmt.execute("CREATE INDEX GroupOwnerIndex ON Groups (owner)");

					// Admins
					stmt.execute("CREATE TABLE GroupAdmins (group_id GroupID, admin QoraAddress, reference Signature NOT NULL, "
							+ "PRIMARY KEY (group_id, admin), FOREIGN KEY (group_id) REFERENCES Groups (group_id) ON DELETE CASCADE)");
					// For finding groups that address administrates
					stmt.execute("CREATE INDEX GroupAdminIndex ON GroupAdmins (admin)");

					// Members
					stmt.execute(
							"CREATE TABLE GroupMembers (group_id GroupID, address QoraAddress, joined TIMESTAMP WITH TIME ZONE NOT NULL, reference Signature NOT NULL, "
									+ "PRIMARY KEY (group_id, address), FOREIGN KEY (group_id) REFERENCES Groups (group_id) ON DELETE CASCADE)");
					// For finding groups that address is member
					stmt.execute("CREATE INDEX GroupMemberIndex ON GroupMembers (address)");

					// Invites
					stmt.execute("CREATE TABLE GroupInvites (group_id GroupID, inviter QoraAddress, invitee QoraAddress, "
							+ "expiry TIMESTAMP WITH TIME ZONE NOT NULL, reference Signature, "
							+ "PRIMARY KEY (group_id, invitee), FOREIGN KEY (group_id) REFERENCES Groups (group_id) ON DELETE CASCADE)");
					// For finding invites sent by inviter
					stmt.execute("CREATE INDEX GroupInviteInviterIndex ON GroupInvites (inviter)");
					// For finding invites by group
					stmt.execute("CREATE INDEX GroupInviteInviteeIndex ON GroupInvites (invitee)");
					// For expiry maintenance
					stmt.execute("CREATE INDEX GroupInviteExpiryIndex ON GroupInvites (expiry)");

					// Pending "join requests"
					stmt.execute(
							"CREATE TABLE GroupJoinRequests (group_id GroupID, joiner QoraAddress, reference Signature NOT NULL, PRIMARY KEY (group_id, joiner))");

					// Bans
					// NULL expiry means does not expire!
					stmt.execute(
							"CREATE TABLE GroupBans (group_id GroupID, offender QoraAddress, admin QoraAddress NOT NULL, banned TIMESTAMP WITH TIME ZONE NOT NULL, "
									+ "reason GenericDescription NOT NULL, expiry TIMESTAMP WITH TIME ZONE, reference Signature NOT NULL, "
									+ "PRIMARY KEY (group_id, offender), FOREIGN KEY (group_id) REFERENCES Groups (group_id) ON DELETE CASCADE)");
					// For expiry maintenance
					stmt.execute("CREATE INDEX GroupBanExpiryIndex ON GroupBans (expiry)");
					break;

				case 29:
					// Account group transactions
					stmt.execute("CREATE TABLE CreateGroupTransactions (signature Signature, creator QoraPublicKey NOT NULL, group_name GroupName NOT NULL, "
							+ "owner QoraAddress NOT NULL, description GenericDescription NOT NULL, is_open BOOLEAN NOT NULL, group_id GroupID, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					stmt.execute("CREATE TABLE UpdateGroupTransactions (signature Signature, owner QoraPublicKey NOT NULL, group_id GroupID NOT NULL, "
							+ "new_owner QoraAddress NOT NULL, new_description GenericDescription NOT NULL, new_is_open BOOLEAN NOT NULL, group_reference Signature, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");

					// Account group add/remove admin transactions
					stmt.execute(
							"CREATE TABLE AddGroupAdminTransactions (signature Signature, owner QoraPublicKey NOT NULL, group_id GroupID NOT NULL, address QoraAddress NOT NULL, "
									+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					stmt.execute(
							"CREATE TABLE RemoveGroupAdminTransactions (signature Signature, owner QoraPublicKey NOT NULL, group_id GroupID NOT NULL, admin QoraAddress NOT NULL, "
									+ "admin_reference Signature, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");

					// Account group join/leave transactions
					stmt.execute("CREATE TABLE JoinGroupTransactions (signature Signature, joiner QoraPublicKey NOT NULL, group_id GroupID NOT NULL, "
							+ "invite_reference Signature, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					stmt.execute("CREATE TABLE LeaveGroupTransactions (signature Signature, leaver QoraPublicKey NOT NULL, group_id GroupID NOT NULL, "
							+ "member_reference Signature, admin_reference Signature, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");

					// Account group kick transaction
					stmt.execute(
							"CREATE TABLE GroupKickTransactions (signature Signature, admin QoraPublicKey NOT NULL, group_id GroupID NOT NULL, address QoraAddress NOT NULL, "
									+ "reason GroupReason, member_reference Signature, admin_reference Signature, join_reference Signature, "
									+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");

					// Account group invite/cancel-invite transactions
					stmt.execute(
							"CREATE TABLE GroupInviteTransactions (signature Signature, admin QoraPublicKey NOT NULL, group_id GroupID NOT NULL, invitee QoraAddress NOT NULL, "
									+ "time_to_live INTEGER NOT NULL, join_reference Signature, "
									+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// Cancel group invite
					stmt.execute(
							"CREATE TABLE CancelGroupInviteTransactions (signature Signature, admin QoraPublicKey NOT NULL, group_id GroupID NOT NULL, invitee QoraAddress NOT NULL, "
									+ "invite_reference Signature, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");

					// Account ban/cancel-ban transactions
					stmt.execute(
							"CREATE TABLE GroupBanTransactions (signature Signature, admin QoraPublicKey NOT NULL, group_id GroupID NOT NULL, address QoraAddress NOT NULL, "
									+ "reason GroupReason, time_to_live INTEGER NOT NULL, "
									+ "member_reference Signature, admin_reference Signature, join_invite_reference Signature, "
									+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					stmt.execute(
							"CREATE TABLE CancelGroupBanTransactions (signature Signature, admin QoraPublicKey NOT NULL, group_id GroupID NOT NULL, address QoraAddress NOT NULL, "
									+ "ban_reference Signature, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					break;

				case 30:
					// Networking
					stmt.execute("CREATE TABLE Peers (hostname VARCHAR(255), port INTEGER, last_connected TIMESTAMP WITH TIME ZONE, last_attempted TIMESTAMP WITH TIME ZONE, "
							+ "last_height INTEGER, last_misbehaved TIMESTAMP WITH TIME ZONE, PRIMARY KEY (hostname, port))");
					break;

				case 31:
					stmt.execute("SET DATABASE TRANSACTION CONTROL MVCC"); // Use MVCC over default two-phase locking, a-k-a "LOCKS"
					break;

				case 32:
					// Unified PeerAddress requires peer hostname & port stored as one string
					stmt.execute("ALTER TABLE Peers ALTER COLUMN hostname RENAME TO address");
					// Make sure literal IPv6 addresses are enclosed in square brackets.
					stmt.execute("UPDATE Peers SET address=CONCAT('[', address, ']') WHERE POSITION(':' IN address) != 0");
					stmt.execute("UPDATE Peers SET address=CONCAT(address, ':', port)");
					// We didn't name the PRIMARY KEY constraint when creating Peers table, so can't easily drop it
					// Workaround is to create a new table with new constraint, drop old table, then rename.
					stmt.execute("CREATE TABLE PeersTEMP AS (SELECT * FROM Peers) WITH DATA");
					stmt.execute("ALTER TABLE PeersTEMP DROP COLUMN port");
					stmt.execute("ALTER TABLE PeersTEMP ADD PRIMARY KEY (address)");
					stmt.execute("DROP TABLE Peers");
					stmt.execute("ALTER TABLE PeersTEMP RENAME TO Peers");
					break;

				case 33:
					// Add groupID to all transactions - groupID 0 is default, which means groupless/no-group
					stmt.execute("ALTER TABLE Transactions ADD COLUMN tx_group_id GroupID NOT NULL DEFAULT 0");
					stmt.execute("CREATE INDEX TransactionGroupIndex ON Transactions (tx_group_id)");

					// Adding approval to group-based transactions
					// Default approval threshold is 100% for existing groups but probably of no effect in production
					stmt.execute("ALTER TABLE Groups ADD COLUMN approval_threshold TINYINT NOT NULL DEFAULT 100 BEFORE reference");
					stmt.execute("ALTER TABLE CreateGroupTransactions ADD COLUMN approval_threshold TINYINT NOT NULL DEFAULT 100 BEFORE group_id");
					stmt.execute("ALTER TABLE UpdateGroupTransactions ADD COLUMN new_approval_threshold TINYINT NOT NULL DEFAULT 100 BEFORE group_reference");

					// Approval transactions themselves
					// "pending_signature" contains signature of pending transaction requiring approval
					// "prior_reference" contains signature of previous approval transaction for orphaning purposes
					stmt.execute("CREATE TABLE GroupApprovalTransactions (signature Signature, admin QoraPublicKey NOT NULL, pending_signature Signature NOT NULL, approval BOOLEAN NOT NULL, "
							+ "prior_reference Signature, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");

					// Accounts have a default groupID to be used if transaction's txGroupId is 0
					stmt.execute("ALTER TABLE Accounts add default_group_id GroupID NOT NULL DEFAULT 0");
					break;

				case 34:
					// SET_GROUP transaction support
					stmt.execute("CREATE TABLE SetGroupTransactions (signature Signature, default_group_id GroupID NOT NULL, previous_default_group_id GroupID, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					break;

				case 35:
					// Group-based transaction approval min/max block delay
					stmt.execute("ALTER TABLE Groups ADD COLUMN min_block_delay INT NOT NULL DEFAULT 0 BEFORE reference");
					stmt.execute("ALTER TABLE Groups ADD COLUMN max_block_delay INT NOT NULL DEFAULT 1440 BEFORE reference");
					stmt.execute("ALTER TABLE CreateGroupTransactions ADD COLUMN min_block_delay INT NOT NULL DEFAULT 0 BEFORE group_id");
					stmt.execute("ALTER TABLE CreateGroupTransactions ADD COLUMN max_block_delay INT NOT NULL DEFAULT 1440 BEFORE group_id");
					stmt.execute("ALTER TABLE UpdateGroupTransactions ADD COLUMN new_min_block_delay INT NOT NULL DEFAULT 0 BEFORE group_reference");
					stmt.execute("ALTER TABLE UpdateGroupTransactions ADD COLUMN new_max_block_delay INT NOT NULL DEFAULT 1440 BEFORE group_reference");
					break;

				case 36:
					// Adding group-ness to record types that could require approval for their related transactions
					// e.g. REGISTER_NAME might require approval and so Names table requires groupID
					// Registered Names
					stmt.execute("ALTER TABLE Names ADD COLUMN creation_group_id GroupID NOT NULL DEFAULT 0");
					// Assets aren't ever updated so don't need group-ness
					// for future use: stmt.execute("ALTER TABLE Assets ADD COLUMN creation_group_id GroupID NOT NULL DEFAULT 0");
					// Polls aren't ever updated, only voted upon using option index so don't need group-ness
					// for future use: stmt.execute("ALTER TABLE Polls ADD COLUMN creation_group_id GroupID NOT NULL DEFAULT 0");
					// CIYAM ATs
					stmt.execute("ALTER TABLE ATs ADD COLUMN creation_group_id GroupID NOT NULL DEFAULT 0");
					// Groups can be updated but updates require approval from original groupID
					stmt.execute("ALTER TABLE Groups ADD COLUMN creation_group_id GroupID NOT NULL DEFAULT 0");
					break;

				case 37:
					// Performance-improving INDEX
					stmt.execute("CREATE INDEX IF NOT EXISTS BlockGenerationHeightIndex ON Blocks (generation, height)");
					// Asset orders now have isClosed=true when isFulfilled=true
					stmt.execute("UPDATE AssetOrders SET is_closed = TRUE WHERE is_fulfilled = TRUE");
					break;

				case 38:
					// Rename asset trade columns for clarity
					stmt.execute("ALTER TABLE AssetTrades ALTER COLUMN amount RENAME TO target_amount");
					stmt.execute("ALTER TABLE AssetTrades ALTER COLUMN price RENAME TO initiator_amount");
					// Add support for asset "data" - typically JSON map like registered name data
					stmt.execute("CREATE TYPE AssetData AS VARCHAR(4000)");
					stmt.execute("ALTER TABLE Assets ADD data AssetData NOT NULL DEFAULT '' BEFORE reference");
					stmt.execute("ALTER TABLE Assets ADD creation_group_id GroupID NOT NULL DEFAULT 0 BEFORE reference");
					// Add support for asset "data" to ISSUE_ASSET transaction
					stmt.execute("ALTER TABLE IssueAssetTransactions ADD data AssetData NOT NULL DEFAULT '' BEFORE asset_id");
					// Add support for UPDATE_ASSET transactions
					stmt.execute("CREATE TABLE UpdateAssetTransactions (signature Signature, owner QoraPublicKey NOT NULL, asset_id AssetID NOT NULL, "
									+ "new_owner QoraAddress NOT NULL, new_description GenericDescription NOT NULL, new_data AssetData NOT NULL, "
									+ "orphan_reference Signature, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// Correct Assets.reference to use ISSUE_ASSET transaction's signature instead of reference.
					// This is to help UPDATE_ASSET orphaning.
					stmt.execute("MERGE INTO Assets USING (SELECT asset_id, signature FROM Assets JOIN Transactions USING (reference) JOIN IssueAssetTransactions USING (signature)) AS Updates "
							+ "ON Assets.asset_id = Updates.asset_id WHEN MATCHED THEN UPDATE SET Assets.reference = Updates.signature");
					break;

				case 39:
					// Support for automatically setting joiner's default groupID when they join a group (by JOIN_GROUP or corresponding admin's INVITE_GROUP)
					stmt.execute("ALTER TABLE JoinGroupTransactions ADD previous_group_id INTEGER");
					stmt.execute("ALTER TABLE GroupInviteTransactions ADD previous_group_id INTEGER");
					// Ditto for leaving
					stmt.execute("ALTER TABLE LeaveGroupTransactions ADD previous_group_id INTEGER");
					stmt.execute("ALTER TABLE GroupKickTransactions ADD previous_group_id INTEGER");
					stmt.execute("ALTER TABLE GroupBanTransactions ADD previous_group_id INTEGER");
					break;

				case 40:
					// Increase asset "data" size from 4K to 400K
					stmt.execute("CREATE TYPE AssetDataLob AS CLOB(400K)");
					stmt.execute("ALTER TABLE Assets ALTER COLUMN data AssetDataLob");
					stmt.execute("ALTER TABLE IssueAssetTransactions ALTER COLUMN data AssetDataLob");
					stmt.execute("ALTER TABLE UpdateAssetTransactions ALTER COLUMN new_data AssetDataLob");
					break;

				case 41:
					// New asset pricing
					/*
					 * We store "unit price" for asset orders but need enough precision to accurately
					 * represent fractional values without loss.
					 * Asset quantities can be up to either 1_000_000_000_000_000_000 (19 digits) if indivisible,
					 * or 10_000_000_000.00000000 (11+8 = 19 digits) if divisible.
					 * Two 19-digit numbers need 38 integer and 38 fractional to cover extremes of unit price.
					 * However, we use another 10 more fractional digits to avoid rounding issues.
					 * 38 integer + 48 fractional gives 86, so: DECIMAL (86, 48)
					 */
					// Rename price to unit_price to preserve indexes
					stmt.execute("ALTER TABLE AssetOrders ALTER COLUMN price RENAME TO unit_price");
					// Adjust precision
					stmt.execute("ALTER TABLE AssetOrders ALTER COLUMN unit_price DECIMAL(76,48)");
					// Add want-amount column
					stmt.execute("ALTER TABLE AssetOrders ADD want_amount QoraAmount BEFORE unit_price");
					// Calculate want-amount values
					stmt.execute("UPDATE AssetOrders set want_amount = amount * unit_price");
					// want-amounts all set, so disallow NULL
					stmt.execute("ALTER TABLE AssetOrders ALTER COLUMN want_amount SET NOT NULL");
					// Rename corresponding column in CreateAssetOrderTransactions
					stmt.execute("ALTER TABLE CreateAssetOrderTransactions ALTER COLUMN price RENAME TO want_amount");
					break;

				case 42:
					// New asset pricing #2
					/*
					 *  Use "price" (discard want-amount) but enforce pricing units in one direction
					 *  to avoid all the reciprocal and round issues.
					 */
					stmt.execute("ALTER TABLE CreateAssetOrderTransactions ALTER COLUMN want_amount RENAME TO price");
					stmt.execute("ALTER TABLE AssetOrders DROP COLUMN want_amount");
					stmt.execute("ALTER TABLE AssetOrders ALTER COLUMN unit_price RENAME TO price");
					stmt.execute("ALTER TABLE AssetOrders ALTER COLUMN price QoraAmount");
					/*
					 *  Normalize any 'old' orders to 'new' pricing.
					 *  We must do this so that requesting open orders can be sorted by price.
					 */
					// Make sure new asset pricing timestamp (used below) is UTC
					stmt.execute("SET TIME ZONE INTERVAL '0:00' HOUR TO MINUTE");
					// Normalize amount/fulfilled to asset with highest assetID, BEFORE price correction
					stmt.execute("UPDATE AssetOrders SET amount = amount * price, fulfilled = fulfilled * price "
							+ "WHERE ordered < timestamp(" + BlockChain.getInstance().getNewAssetPricingTimestamp() + ") "
							+ "AND have_asset_id < want_asset_id");
					// Normalize price into lowest-assetID/highest-assetID price-pair, e.g. QORA/asset100
					// Note: HSQLDB uses BigDecimal's dividend.divide(divisor, RoundingMode.DOWN) too
					stmt.execute("UPDATE AssetOrders SET price = CAST(1 AS QoraAmount) / price "
							+ "WHERE ordered < timestamp(" + BlockChain.getInstance().getNewAssetPricingTimestamp() + ") "
							+ "AND have_asset_id < want_asset_id");
					// Revert time zone change above
					stmt.execute("SET TIME ZONE LOCAL");
					break;

				case 43:
					// More work on 'new' asset pricing - refunds due to price improvement
					stmt.execute("ALTER TABLE AssetTrades ADD initiator_saving QoraAmount NOT NULL DEFAULT 0");
					break;

				case 44:
					// Account flags
					stmt.execute("ALTER TABLE Accounts ADD COLUMN flags INT NOT NULL DEFAULT 0");
					// Corresponding transaction to set/clear flags
					stmt.execute("CREATE TABLE AccountFlagsTransactions (signature Signature, creator QoraPublicKey NOT NULL, target QoraAddress NOT NULL, and_mask INT NOT NULL, or_mask INT NOT NULL, xor_mask INT NOT NULL, "
							+ "previous_flags INT, PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					break;

				case 45:
					// Enabling other accounts to forge
					// Transaction to allow one account to enable other account to forge
					stmt.execute("CREATE TABLE EnableForgingTransactions (signature Signature, creator QoraPublicKey NOT NULL, target QoraAddress NOT NULL, "
							+ "PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// Modification to accounts to record who enabled them to forge (useful for counting accounts and potentially orphaning)
					stmt.execute("ALTER TABLE Accounts ADD COLUMN forging_enabler QoraAddress");
					break;

				case 46:
					// Proxy forging
					// Transaction emitted by forger announcing they are forging on behalf of recipient
					stmt.execute("CREATE TABLE ProxyForgingTransactions (signature Signature, forger QoraPublicKey NOT NULL, recipient QoraAddress NOT NULL, proxy_public_key QoraPublicKey NOT NULL, share DECIMAL(5,2) NOT NULL, "
							+ "previous_share DECIMAL(5,2), PRIMARY KEY (signature), FOREIGN KEY (signature) REFERENCES Transactions (signature) ON DELETE CASCADE)");
					// Table of current shares
					stmt.execute("CREATE TABLE ProxyForgers (forger QoraPublicKey NOT NULL, recipient QoraAddress NOT NULL, proxy_public_key QoraPublicKey NOT NULL, share DECIMAL(5,2) NOT NULL, "
							+ "PRIMARY KEY (forger, recipient))");
					// Proxy-forged blocks will contain proxy public key, which will be used to look up block reward sharing, so create index for those lookups
					stmt.execute("CREATE INDEX ProxyForgersProxyPublicKeyIndex ON ProxyForgers (proxy_public_key)");
					break;

				case 47:
					// Stash of private keys used for generating blocks. These should be proxy keys!
					stmt.execute("CREATE TYPE QoraKeySeed AS VARBINARY(32)");
					stmt.execute("CREATE TABLE ForgingAccounts (forger_seed QoraKeySeed NOT NULL, PRIMARY KEY (forger_seed))");
					break;

				case 48:
					// Add index to TransactionParticipants to speed up queries
					stmt.execute("CREATE INDEX TransactionParticipantsAddressIndex on TransactionParticipants (participant)");
					break;

				case 49:
					// Additional peer information
					stmt.execute("ALTER TABLE Peers ADD COLUMN last_block_signature BlockSignature BEFORE last_misbehaved");
					stmt.execute("ALTER TABLE Peers ADD COLUMN last_block_timestamp TIMESTAMP WITH TIME ZONE BEFORE last_misbehaved");
					stmt.execute("ALTER TABLE Peers ADD COLUMN last_block_generator QoraPublicKey BEFORE last_misbehaved");
					break;

				default:
					// nothing to do
					return false;
			}
		}

		// database was updated
		LOGGER.info(String.format("HSQLDB repository updated to version %d", databaseVersion + 1));
		return true;
	}

}
