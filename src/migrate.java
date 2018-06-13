import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import com.google.common.hash.HashCode;
import com.google.common.io.CharStreams;

import qora.transaction.Transaction;
import repository.BlockRepository;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import utils.Base58;

public class migrate {

	private static final String GENESIS_ADDRESS = "QfGMeDQQUQePMpAmfLBJzgqyrM35RWxHGD";
	private static final byte[] GENESIS_PUBLICKEY = new byte[] { 1, 1, 1, 1, 1, 1, 1, 1 };

	private static Map<String, byte[]> publicKeyByAddress = new HashMap<String, byte[]>();

	public static Object fetchBlockJSON(int height) throws IOException {
		InputStream is;

		try {
			is = new URL("http://localhost:9085/blocks/byheight/" + height).openStream();
		} catch (IOException e) {
			return null;
		}

		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			return JSONValue.parseWithException(reader);
		} catch (ParseException e) {
			return null;
		} finally {
			is.close();
		}
	}

	public static byte[] addressToPublicKey(String address) throws IOException {
		byte[] cachedPublicKey = publicKeyByAddress.get(address);
		if (cachedPublicKey != null)
			return cachedPublicKey;

		InputStream is = new URL("http://localhost:9085/addresses/publickey/" + address).openStream();

		try {
			String publicKey58 = CharStreams.toString(new InputStreamReader(is, Charset.forName("UTF-8")));

			byte[] publicKey = Base58.decode(publicKey58);
			publicKeyByAddress.put(address, publicKey);
			return publicKey;
		} finally {
			is.close();
		}
	}

	public static String formatWithPlaceholders(String... columns) {
		String[] placeholders = new String[columns.length];
		Arrays.setAll(placeholders, (int i) -> "?");

		StringBuilder output = new StringBuilder();
		output.append("(");
		output.append(String.join(", ", columns));
		output.append(") VALUES (");
		output.append(String.join(", ", placeholders));
		output.append(")");
		return output.toString();
	}

	public static void main(String args[]) throws SQLException, DataException, IOException {
		// Genesis public key
		publicKeyByAddress.put(GENESIS_ADDRESS, GENESIS_PUBLICKEY);
		// Some other public keys for addresses that have never created a transaction
		publicKeyByAddress.put("QcDLhirHkSbR4TLYeShLzHw61B8UGTFusk", Base58.decode("HP58uWRBae654ze6ysmdyGv3qaDrr9BEk6cHv4WuiF7d"));

		// TODO convert to repository
		Connection c = null;

		test.Common.setRepository();
		Repository repository = RepositoryManager.getRepository();
		BlockRepository blockRepository = repository.getBlockRepository();

		PreparedStatement blocksPStmt = c
				.prepareStatement("INSERT INTO Blocks " + formatWithPlaceholders("signature", "version", "reference", "transaction_count", "total_fees",
						"transactions_signature", "height", "generation", "generating_balance", "generator", "generator_signature", "AT_data", "AT_fees"));

		PreparedStatement txPStmt = c.prepareStatement(
				"INSERT INTO Transactions " + formatWithPlaceholders("signature", "reference", "type", "creator", "creation", "fee", "milestone_block"));

		PreparedStatement recipientPStmt = c.prepareStatement("INSERT INTO TransactionRecipients " + formatWithPlaceholders("signature", "recipient"));

		PreparedStatement genesisPStmt = c.prepareStatement("INSERT INTO GenesisTransactions " + formatWithPlaceholders("signature", "recipient", "amount"));
		PreparedStatement paymentPStmt = c
				.prepareStatement("INSERT INTO PaymentTransactions " + formatWithPlaceholders("signature", "sender", "recipient", "amount"));
		PreparedStatement registerNamePStmt = c
				.prepareStatement("INSERT INTO RegisterNameTransactions " + formatWithPlaceholders("signature", "registrant", "name", "owner", "data"));
		PreparedStatement updateNamePStmt = c
				.prepareStatement("INSERT INTO UpdateNameTransactions " + formatWithPlaceholders("signature", "owner", "name", "new_owner", "new_data"));
		PreparedStatement sellNamePStmt = c
				.prepareStatement("INSERT INTO SellNameTransactions " + formatWithPlaceholders("signature", "owner", "name", "amount"));
		PreparedStatement cancelSellNamePStmt = c
				.prepareStatement("INSERT INTO CancelSellNameTransactions " + formatWithPlaceholders("signature", "owner", "name"));
		PreparedStatement buyNamePStmt = c
				.prepareStatement("INSERT INTO BuyNameTransactions " + formatWithPlaceholders("signature", "buyer", "name", "seller", "amount"));
		PreparedStatement createPollPStmt = c
				.prepareStatement("INSERT INTO CreatePollTransactions " + formatWithPlaceholders("signature", "creator", "poll", "description"));
		PreparedStatement createPollOptionPStmt = c
				.prepareStatement("INSERT INTO CreatePollTransactionOptions " + formatWithPlaceholders("signature", "option"));
		PreparedStatement voteOnPollPStmt = c
				.prepareStatement("INSERT INTO VoteOnPollTransactions " + formatWithPlaceholders("signature", "voter", "poll", "option_index"));
		PreparedStatement arbitraryPStmt = c
				.prepareStatement("INSERT INTO ArbitraryTransactions " + formatWithPlaceholders("signature", "creator", "service", "data_hash"));
		PreparedStatement issueAssetPStmt = c.prepareStatement("INSERT INTO IssueAssetTransactions "
				+ formatWithPlaceholders("signature", "issuer", "owner", "asset_name", "description", "quantity", "is_divisible"));
		PreparedStatement transferAssetPStmt = c
				.prepareStatement("INSERT INTO TransferAssetTransactions " + formatWithPlaceholders("signature", "sender", "recipient", "asset_id", "amount"));
		PreparedStatement createAssetOrderPStmt = c.prepareStatement("INSERT INTO CreateAssetOrderTransactions "
				+ formatWithPlaceholders("signature", "creator", "have_asset_id", "amount", "want_asset_id", "price"));
		PreparedStatement cancelAssetOrderPStmt = c
				.prepareStatement("INSERT INTO CancelAssetOrderTransactions " + formatWithPlaceholders("signature", "creator", "asset_order"));
		PreparedStatement multiPaymentPStmt = c.prepareStatement("INSERT INTO MultiPaymentTransactions " + formatWithPlaceholders("signature", "sender"));
		PreparedStatement deployATPStmt = c.prepareStatement("INSERT INTO DeployATTransactions "
				+ formatWithPlaceholders("signature", "creator", "AT_name", "description", "AT_type", "AT_tags", "creation_bytes", "amount"));
		PreparedStatement messagePStmt = c.prepareStatement("INSERT INTO MessageTransactions "
				+ formatWithPlaceholders("signature", "version", "sender", "recipient", "is_text", "is_encrypted", "amount", "asset_id", "data"));

		PreparedStatement sharedPaymentPStmt = c
				.prepareStatement("INSERT INTO SharedTransactionPayments " + formatWithPlaceholders("signature", "recipient", "amount", "asset_id"));

		PreparedStatement blockTxPStmt = c
				.prepareStatement("INSERT INTO BlockTransactions " + formatWithPlaceholders("block_signature", "sequence", "transaction_signature"));

		int height = blockRepository.getBlockchainHeight() + 1;
		byte[] milestone_block = null;
		System.out.println("Starting migration from block height " + height);

		while (true) {
			JSONObject json = (JSONObject) fetchBlockJSON(height);
			if (json == null)
				break;

			if (height % 1000 == 0)
				System.out.println("Height: " + height + ", public key map size: " + publicKeyByAddress.size());

			JSONArray transactions = (JSONArray) json.get("transactions");

			// Blocks:
			// signature, version, reference, transaction_count, total_fees, transactions_signature, height, generation, generating_balance, generator,
			// generator_signature
			// varchar, tinyint, varchar, int, decimal, varchar, int, timestamp, decimal, varchar, varchar
			byte[] blockSignature = Base58.decode((String) json.get("signature"));
			byte[] blockReference = Base58.decode((String) json.get("reference"));
			byte[] blockTransactionsSignature = Base58.decode((String) json.get("transactionsSignature"));
			byte[] blockGeneratorSignature = Base58.decode((String) json.get("generatorSignature"));

			byte[] generatorPublicKey = addressToPublicKey((String) json.get("generator"));

			blocksPStmt.setBinaryStream(1, new ByteArrayInputStream(blockSignature));
			blocksPStmt.setInt(2, ((Long) json.get("version")).intValue());
			blocksPStmt.setBinaryStream(3, new ByteArrayInputStream(blockReference));
			blocksPStmt.setInt(4, transactions.size());
			blocksPStmt.setBigDecimal(5, BigDecimal.valueOf(Double.valueOf((String) json.get("fee")).doubleValue()));
			blocksPStmt.setBinaryStream(6, new ByteArrayInputStream(blockTransactionsSignature));
			blocksPStmt.setInt(7, height);
			blocksPStmt.setTimestamp(8, new Timestamp((Long) json.get("timestamp")));
			blocksPStmt.setBigDecimal(9, BigDecimal.valueOf((Long) json.get("generatingBalance")));
			blocksPStmt.setBinaryStream(10, new ByteArrayInputStream(generatorPublicKey));
			blocksPStmt.setBinaryStream(11, new ByteArrayInputStream(blockGeneratorSignature));

			String blockATs = (String) json.get("blockATs");
			if (blockATs != null && blockATs.length() > 0) {
				HashCode atBytes = HashCode.fromString(blockATs);

				blocksPStmt.setBinaryStream(12, new ByteArrayInputStream(atBytes.asBytes()));
				blocksPStmt.setBigDecimal(13, BigDecimal.valueOf(((Long) json.get("atFees")).longValue(), 8));
			} else {
				blocksPStmt.setNull(12, java.sql.Types.VARBINARY);
				blocksPStmt.setNull(13, java.sql.Types.DECIMAL);
			}

			blocksPStmt.execute();
			blocksPStmt.clearParameters();

			// Transactions:
			// signature, reference, type, creator, creation, fee, milestone_block
			// varchar, varchar, int, varchar, timestamp, decimal, varchar
			for (int txIndex = 0; txIndex < transactions.size(); ++txIndex) {
				JSONObject transaction = (JSONObject) transactions.get(txIndex);

				byte[] txSignature = Base58.decode((String) transaction.get("signature"));
				txPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));

				String txReference58 = (String) transaction.get("reference");
				byte[] txReference = txReference58.isEmpty() ? null : Base58.decode(txReference58);
				int type = ((Long) transaction.get("type")).intValue();

				if (txReference != null)
					txPStmt.setBinaryStream(2, new ByteArrayInputStream(txReference));
				else if (height == 1 && type == 1)
					txPStmt.setNull(2, java.sql.Types.VARBINARY); // genesis transactions only
				else
					fail();

				txPStmt.setInt(3, type);

				// Determine transaction "creator" from specific transaction info
				switch (type) {
					case 1: // genesis
						txPStmt.setBinaryStream(4, new ByteArrayInputStream(GENESIS_PUBLICKEY)); // genesis transactions only
						break;

					case 2: // payment
					case 12: // transfer asset
					case 15: // multi-payment
						txPStmt.setBinaryStream(4, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("sender"))));
						break;

					case 3: // register name
						txPStmt.setBinaryStream(4, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("registrant"))));
						break;

					case 4: // update name
					case 5: // sell name
					case 6: // cancel sell name
						txPStmt.setBinaryStream(4, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("owner"))));
						break;

					case 7: // buy name
						txPStmt.setBinaryStream(4, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("buyer"))));
						break;

					case 8: // create poll
					case 9: // vote on poll
					case 10: // arbitrary transaction
					case 11: // issue asset
					case 13: // create asset order
					case 14: // cancel asset order
					case 16: // deploy CIYAM AT
					case 17: // message
						txPStmt.setBinaryStream(4, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));
						break;

					default:
						fail();
				}

				long transactionTimestamp = ((Long) transaction.get("timestamp")).longValue();
				txPStmt.setTimestamp(5, new Timestamp(transactionTimestamp));
				txPStmt.setBigDecimal(6, BigDecimal.valueOf(Double.valueOf((String) transaction.get("fee")).doubleValue()));

				if (milestone_block != null)
					txPStmt.setBinaryStream(7, new ByteArrayInputStream(milestone_block));
				else if (height == 1 && type == 1)
					txPStmt.setNull(7, java.sql.Types.VARBINARY); // genesis transactions only
				else
					fail();

				txPStmt.execute();
				txPStmt.clearParameters();

				JSONArray multiPayments = null;
				if (type == 15)
					multiPayments = (JSONArray) transaction.get("payments");

				List<String> recipients = new ArrayList<String>();
				switch (type) {
					case 1: // genesis
					case 2: // payment
					case 12: // transfer asset
					case 17: // message
						recipients.add((String) transaction.get("recipient"));
						break;

					case 3: // register name
					case 4: // update name
						// parse Name data for "owner"
						break;

					case 5: // sell name
					case 6: // cancel sell name
					case 8: // create poll
					case 9: // vote on poll
					case 10: // arbitrary transaction
					case 13: // create asset order
					case 14: // cancel asset order
					case 16: // deploy CIYAM AT
						// no recipients
						break;

					case 7: // buy name
						recipients.add((String) transaction.get("seller"));
						break;

					case 11: // issue asset
						recipients.add((String) transaction.get("creator"));
						break;

					case 15: // multi-payment
						assertNotNull(multiPayments);
						for (Object payment : multiPayments) {
							String recipient = (String) ((JSONObject) payment).get("recipient");
							recipients.add(recipient);
						}
						break;

					default:
						fail();
				}

				for (String recipient : recipients) {
					recipientPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
					recipientPStmt.setString(2, recipient);

					recipientPStmt.execute();
					recipientPStmt.clearParameters();
				}

				// Transaction-type-specific processing
				switch (type) {
					case 1: // genesis
						genesisPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						genesisPStmt.setString(2, recipients.get(0));
						genesisPStmt.setBigDecimal(3, BigDecimal.valueOf(Double.valueOf((String) transaction.get("amount")).doubleValue()));

						genesisPStmt.execute();
						genesisPStmt.clearParameters();
						break;

					case 2: // payment
						paymentPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						paymentPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("sender"))));
						paymentPStmt.setString(3, recipients.get(0));
						paymentPStmt.setBigDecimal(4, BigDecimal.valueOf(Double.valueOf((String) transaction.get("amount")).doubleValue()));

						paymentPStmt.execute();
						paymentPStmt.clearParameters();
						break;

					case 3: // register name
						registerNamePStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						registerNamePStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("registrant"))));
						registerNamePStmt.setString(3, (String) transaction.get("name"));
						registerNamePStmt.setString(4, (String) transaction.get("owner"));
						registerNamePStmt.setString(5, (String) transaction.get("value"));

						registerNamePStmt.execute();
						registerNamePStmt.clearParameters();
						break;

					case 4: // update name
						updateNamePStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						updateNamePStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("owner"))));
						updateNamePStmt.setString(3, (String) transaction.get("name"));
						updateNamePStmt.setString(4, (String) transaction.get("newOwner"));
						updateNamePStmt.setString(5, (String) transaction.get("newValue"));

						updateNamePStmt.execute();
						updateNamePStmt.clearParameters();
						break;

					case 5: // sell name
						sellNamePStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						sellNamePStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("owner"))));
						sellNamePStmt.setString(3, (String) transaction.get("name"));
						sellNamePStmt.setBigDecimal(4, BigDecimal.valueOf(Double.valueOf((String) transaction.get("amount")).doubleValue()));

						sellNamePStmt.execute();
						sellNamePStmt.clearParameters();
						break;

					case 6: // cancel sell name
						cancelSellNamePStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						cancelSellNamePStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("owner"))));
						cancelSellNamePStmt.setString(3, (String) transaction.get("name"));

						cancelSellNamePStmt.execute();
						cancelSellNamePStmt.clearParameters();
						break;

					case 7: // buy name
						buyNamePStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						buyNamePStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("buyer"))));
						buyNamePStmt.setString(3, (String) transaction.get("name"));
						buyNamePStmt.setString(4, (String) transaction.get("seller"));
						buyNamePStmt.setBigDecimal(5, BigDecimal.valueOf(Double.valueOf((String) transaction.get("amount")).doubleValue()));

						buyNamePStmt.execute();
						buyNamePStmt.clearParameters();
						break;

					case 8: // create poll
						createPollPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						createPollPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));
						createPollPStmt.setString(3, (String) transaction.get("name"));
						createPollPStmt.setString(4, (String) transaction.get("description"));

						createPollPStmt.execute();
						createPollPStmt.clearParameters();

						// options
						JSONArray options = (JSONArray) transaction.get("options");
						for (Object option : options) {
							createPollOptionPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
							createPollOptionPStmt.setString(2, (String) option);

							createPollOptionPStmt.execute();
							createPollOptionPStmt.clearParameters();
						}
						break;

					case 9: // vote on poll
						voteOnPollPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						voteOnPollPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));
						voteOnPollPStmt.setString(3, (String) transaction.get("poll"));
						voteOnPollPStmt.setInt(4, ((Long) transaction.get("option")).intValue());

						voteOnPollPStmt.execute();
						voteOnPollPStmt.clearParameters();
						break;

					case 10: // arbitrary transactions
						arbitraryPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						arbitraryPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));
						arbitraryPStmt.setInt(3, ((Long) transaction.get("service")).intValue());
						arbitraryPStmt.setString(4, "TODO");

						arbitraryPStmt.execute();
						arbitraryPStmt.clearParameters();

						if (multiPayments != null)
							for (Object paymentObj : multiPayments) {
								JSONObject payment = (JSONObject) paymentObj;

								sharedPaymentPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
								sharedPaymentPStmt.setString(2, (String) payment.get("recipient"));
								sharedPaymentPStmt.setBigDecimal(3, BigDecimal.valueOf(Double.valueOf((String) payment.get("amount")).doubleValue()));
								sharedPaymentPStmt.setLong(4, ((Long) payment.get("asset")).longValue());

								sharedPaymentPStmt.execute();
								sharedPaymentPStmt.clearParameters();
							}
						break;

					case 11: // issue asset
						issueAssetPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						issueAssetPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));
						issueAssetPStmt.setString(3, (String) transaction.get("owner"));
						issueAssetPStmt.setString(4, (String) transaction.get("name"));
						issueAssetPStmt.setString(5, (String) transaction.get("description"));
						issueAssetPStmt.setBigDecimal(6, BigDecimal.valueOf(((Long) transaction.get("quantity")).longValue()));
						issueAssetPStmt.setBoolean(7, (Boolean) transaction.get("divisible"));

						issueAssetPStmt.execute();
						issueAssetPStmt.clearParameters();
						break;

					case 12: // transfer asset
						transferAssetPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						transferAssetPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("sender"))));
						transferAssetPStmt.setString(3, (String) transaction.get("recipient"));
						transferAssetPStmt.setLong(4, ((Long) transaction.get("asset")).longValue());
						transferAssetPStmt.setBigDecimal(5, BigDecimal.valueOf(Double.valueOf((String) transaction.get("amount")).doubleValue()));

						transferAssetPStmt.execute();
						transferAssetPStmt.clearParameters();
						break;

					case 13: // create asset order
						createAssetOrderPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						createAssetOrderPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));

						JSONObject assetOrder = (JSONObject) transaction.get("order");
						createAssetOrderPStmt.setLong(3, ((Long) assetOrder.get("have")).longValue());
						createAssetOrderPStmt.setBigDecimal(4, BigDecimal.valueOf(Double.valueOf((String) assetOrder.get("amount")).doubleValue()));
						createAssetOrderPStmt.setLong(5, ((Long) assetOrder.get("want")).longValue());
						createAssetOrderPStmt.setBigDecimal(6, BigDecimal.valueOf(Double.valueOf((String) assetOrder.get("price")).doubleValue()));

						createAssetOrderPStmt.execute();
						createAssetOrderPStmt.clearParameters();
						break;

					case 14: // cancel asset order
						cancelAssetOrderPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						cancelAssetOrderPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));
						cancelAssetOrderPStmt.setString(3, (String) transaction.get("order"));

						cancelAssetOrderPStmt.execute();
						cancelAssetOrderPStmt.clearParameters();
						break;

					case 15: // multi-payment
						multiPaymentPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						multiPaymentPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("sender"))));

						multiPaymentPStmt.execute();
						multiPaymentPStmt.clearParameters();

						for (Object paymentObj : multiPayments) {
							JSONObject payment = (JSONObject) paymentObj;

							sharedPaymentPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
							sharedPaymentPStmt.setString(2, (String) payment.get("recipient"));
							sharedPaymentPStmt.setBigDecimal(3, BigDecimal.valueOf(Double.valueOf((String) payment.get("amount")).doubleValue()));
							sharedPaymentPStmt.setLong(4, ((Long) payment.get("asset")).longValue());

							sharedPaymentPStmt.execute();
							sharedPaymentPStmt.clearParameters();
						}
						break;

					case 16: // deploy AT
						HashCode creationBytes = HashCode.fromString((String) transaction.get("creationBytes"));
						InputStream creationBytesStream = new ByteArrayInputStream(creationBytes.asBytes());

						deployATPStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						deployATPStmt.setBinaryStream(2, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));
						deployATPStmt.setString(3, (String) transaction.get("name"));
						deployATPStmt.setString(4, (String) transaction.get("description"));
						deployATPStmt.setString(5, (String) transaction.get("atType"));
						deployATPStmt.setString(6, (String) transaction.get("tags"));
						deployATPStmt.setBinaryStream(7, creationBytesStream);
						deployATPStmt.setBigDecimal(8, BigDecimal.valueOf(Double.valueOf((String) transaction.get("amount")).doubleValue()));

						deployATPStmt.execute();
						deployATPStmt.clearParameters();
						break;

					case 17: // message
						boolean isText = (Boolean) transaction.get("isText");
						boolean isEncrypted = (Boolean) transaction.get("encrypted");
						String messageData = (String) transaction.get("data");

						InputStream messageDataStream;
						if (isText && !isEncrypted) {
							messageDataStream = new ByteArrayInputStream(messageData.getBytes("UTF-8"));
						} else {
							HashCode messageBytes = HashCode.fromString(messageData);
							messageDataStream = new ByteArrayInputStream(messageBytes.asBytes());
						}

						messagePStmt.setBinaryStream(1, new ByteArrayInputStream(txSignature));
						messagePStmt.setInt(2, Transaction.getVersionByTimestamp(transactionTimestamp));
						messagePStmt.setBinaryStream(3, new ByteArrayInputStream(addressToPublicKey((String) transaction.get("creator"))));
						messagePStmt.setString(4, (String) transaction.get("recipient"));
						messagePStmt.setBoolean(5, isText);
						messagePStmt.setBoolean(6, isEncrypted);
						messagePStmt.setBigDecimal(7, BigDecimal.valueOf(Double.valueOf((String) transaction.get("amount")).doubleValue()));

						if (transaction.containsKey("asset"))
							messagePStmt.setLong(8, ((Long) transaction.get("asset")).longValue());
						else
							messagePStmt.setLong(8, 0L); // QORA simulated asset

						messagePStmt.setBinaryStream(9, messageDataStream);

						messagePStmt.execute();
						messagePStmt.clearParameters();
						break;

					default:
						// fail();
				}

				blockTxPStmt.setBinaryStream(1, new ByteArrayInputStream(blockSignature));
				blockTxPStmt.setInt(2, txIndex);
				blockTxPStmt.setBinaryStream(3, new ByteArrayInputStream(txSignature));

				blockTxPStmt.execute();
				blockTxPStmt.clearParameters();

				repository.saveChanges();
			}

			// new milestone block every 500 blocks?
			if (milestone_block == null || (height % 500) == 0)
				milestone_block = blockSignature;

			++height;
		}

		System.out.println("Migration finished with new blockchain height " + blockRepository.getBlockchainHeight());
	}

}
