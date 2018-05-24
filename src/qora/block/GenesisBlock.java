package qora.block;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import qora.account.GenesisAccount;
import qora.account.PrivateKeyAccount;
import qora.crypto.Crypto;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;

public class GenesisBlock extends Block {

	private static GenesisBlock instance;

	private static final int GENESIS_BLOCK_VERSION = 1;
	private static final byte[] GENESIS_REFERENCE = new byte[] { 1, 1, 1, 1, 1, 1, 1, 1 }; // NOTE: Neither 64 nor 128 bytes!
	private static final BigDecimal GENESIS_GENERATION_TARGET = BigDecimal.valueOf(10_000_000L).setScale(8);
	private static final GenesisAccount GENESIS_GENERATOR = new GenesisAccount();
	private static final long GENESIS_TIMESTAMP = 1400247274336L; // QORA RELEASE: Fri May 16 13:34:34.336 2014 UTC
	private static final byte[] GENESIS_GENERATION_SIGNATURE = calcSignature();
	private static final byte[] GENESIS_TRANSACTIONS_SIGNATURE = calcSignature();

	// Constructors
	protected GenesisBlock() {
		super(GENESIS_BLOCK_VERSION, GENESIS_REFERENCE, GENESIS_TIMESTAMP, GENESIS_GENERATION_TARGET, GENESIS_GENERATOR, GENESIS_GENERATION_SIGNATURE, null,
				null);

		this.height = 1;

		this.transactions = new ArrayList<Transaction>();

		// Genesis transactions
		addGenesisTransaction("QUD9y7NZqTtNwvSAUfewd7zKUGoVivVnTW", "7032468.191");
		addGenesisTransaction("QVafvKkE5bZTkq8PcXvdaxwuLNN2DGCwYk", "1716146.084");
		addGenesisTransaction("QV42QQP7frYWqsVq536g7zSk97fUpf2ZSN", "5241707.06");
		addGenesisTransaction("QgkLTm5GkepJpgr53nAgUyYRsvmyHpb2zT", "854964.0816");
		addGenesisTransaction("Qc8kN338XQULMBuUa6mTqL5tipvELDhzeZ", "769467.6734");
		addGenesisTransaction("QQ81BA75jZcpjQBLZE1qcHrXV8ARC1DEec", "85496408.16");
		addGenesisTransaction("QeoSe4DscWX4AFkNBCdm4WS1V7QkUFSQLP", "854968.3564");
		addGenesisTransaction("Qdfu3Eh21ZVHNDY1xyNaFqTTEYscSmfSsm", "85496408.16");
		addGenesisTransaction("QeDSr4abXKRg9j5hTN3TK9UGuH3umThZ42", "4445813.224");
		addGenesisTransaction("QQKDuo1txYB9E2xim79YVR6SQ1ZbJtJtFX", "47023024.49");
		addGenesisTransaction("QLeaeGr4CDA95FmeMtFh8okJRMLoq8Cge5", "170992816.3");
		addGenesisTransaction("QSwN5oa8ZHWJmc6FeAJ8Xr1SHaEuSahw1J", "3419856.326");
		addGenesisTransaction("QWnoGd4a7iXqQmNEpUtCb1x7nWgcya8QbE", "17056533.43");
		addGenesisTransaction("QbJqhsJjcy3vkzsJ1kHvgn26pQF3sZEypc", "42705455.87");
		addGenesisTransaction("QiBhBcseKzaDnHKyqEJs8z1Xx2rSb9XhBr", "141069073.5");
		addGenesisTransaction("QTwYwxBhzivFEWY5yfzyz1pqhJ8XCroKwv", "85496408.16");
		addGenesisTransaction("QfikxUU15Dy1oxbcDNEcLeU5cHvbrceq3A", "17099281.63");
		addGenesisTransaction("QhdqBmKZeQ3Hg1XUuR5nKtAkw47tuoRi2q", "12824461.22");
		addGenesisTransaction("QaVNyTqsTHA6JWMcqntcJf1u9c3qid76xH", "128244612.2");
		addGenesisTransaction("QYaDa7bmgo5L9qkcfJKjhPPrQkvGjEoc7y", "85496408.16");
		addGenesisTransaction("QQPddvWaYf4pbCyVHEVoyfC72EiaAv4JhT", "25648922.45");
		addGenesisTransaction("QSQpTNtTZMwaDuNq56Jz73KHWXaey9JrT1", "26341443.35");
		addGenesisTransaction("QVjcFWE6TnGePGJEtbNc1thwD2sgHBLvUV", "42940528.25");
		addGenesisTransaction("Qga93mWNqTuJYx6o33vjUpFH7Cn4sxLyoG", "2564892.245");
		addGenesisTransaction("QXyHKyQPJnb4ejyTkvS26x9sjWnhTTJ1Uc", "10259568.98");
		addGenesisTransaction("QLurSSgJvW7WXHDFSobgfakgqXxjoZzwUH", "85496408.16");
		addGenesisTransaction("QadxfiARcsmwzE93wqq82yXFi3ykM2qdtS", "79118376.11");
		addGenesisTransaction("QRHhhtz3Cv9RPKB1QBBfkRmRfpXx8vkRa5", "22435418.54");
		addGenesisTransaction("Qh8UnEs55n8jcnBaBwVtrTGkFFFBDyrMqH", "128757590.7");
		addGenesisTransaction("QhF7Fu3f54CTYA7zBQ223NQEssi2yAbAcx", "258481290.8");
		addGenesisTransaction("QPk9VB6tigoifrUYQrw4arBNk7i8HEgsDD", "128244612.2");
		addGenesisTransaction("QXWJnEPsdtaLQAEutJFR4ySiMUJCWDzZJX", "85496408.16");
		addGenesisTransaction("QVFs42gM4Cixf4Y5vDFvKKxRAamUPMCAVq", "85496408.16");
		addGenesisTransaction("Qec5ueWc4rcBrty47GZfFSqvLymxvcycFm", "129091026.7");
		addGenesisTransaction("QfYiztbDz1Nb9EMhgHidLycvuPN8HEcHEj", "128244612.2");
		addGenesisTransaction("QPdWsZtaZcAKqk2HWVhEVbws4qG5KUTXmg", "179285967.9");
		addGenesisTransaction("QVkNs5NcwQpsrCXpWzuMXkMehJr5mkvLVy", "8558190.456");
		addGenesisTransaction("Qg19DzyEfyZANx6JLy4GrSGF5LuZ2MLqyZ", "42748204.08");
		addGenesisTransaction("Qf3A8L5WJNHt1xZxmayrTp2d5owzdkcxM6", "50519827.58");
		addGenesisTransaction("QeKR4W6qkFJGF7Hmu7rSUzTSQiqJzZLXdt", "10216820.77");
		addGenesisTransaction("QWg7T5i3uBY3xeBLFTLYYruR15Ln11vwo4", "170992816.3");
		addGenesisTransaction("QUYdM5fHECPZxKQQAmoxoQa2aWg8TZYfPw", "85496408.16");
		addGenesisTransaction("QjhfEZCgrjUbnLRnWqWxzyYqKQpjjxkuA8", "86665653.61");
		addGenesisTransaction("QMA53u3wrzDoxC57CWUJePNdR8FoqinqUS", "85496408.16");
		addGenesisTransaction("QSuCp6mB5zNNeJKD62aq2hR9h84ks1WhHf", "161588211.4");
		addGenesisTransaction("QS2tCUk7GQefg4zGewwrumxSPmN6fgA7Xc", "170992816.3");
		addGenesisTransaction("Qcn6FZRxAgp3japtvjgUkBY6KPfbPZMZtM", "170992816.3");
		addGenesisTransaction("QZrmXZkRmjV2GwMt72Rr1ZqHJjv8raDk5J", "17099281.63");
		addGenesisTransaction("QeZzwGDfAHa132jb6r4rQHbgJstLuT8QJ3", "255875360.3");
		addGenesisTransaction("Qj3L139sMMuFvvjKQDwRnoSgKUnoMhDQs5", "76946767.34");
		addGenesisTransaction("QWJvpvbFRZHu7LRbY5MjzvrMBgzJNFYjCX", "178251461.4");
		addGenesisTransaction("QRyECqW54ywKVt4kZTEXyRY17aaFUaxzc4", "8772355.539");
		addGenesisTransaction("QgpH3K3ArkQTg15xjKqGq3BRgE3aNH9Q2P", "46766535.26");
		addGenesisTransaction("QVZ6pxi8e3K3S44zLbnrLSLwSoYT8CWbwV", "233172022.2");
		addGenesisTransaction("QNbA69dbnmwqJHLQeS9v63hSLZXXGkmtC6", "46626632.05");
		addGenesisTransaction("QgzudSKbcLUeQUhFngotVswDSkbU42dSMr", "83786479.99");
		addGenesisTransaction("QfkQ2UzKMBGPwj8Sm31SArjtXoka1ubU3i", "116345066.7");
		addGenesisTransaction("QgxHHNwawZeTmQ3i5d9enchi4T9VmzNZ5k", "155448014.8");
		addGenesisTransaction("QMNugJWNsLuV4Qmbzdf8r8RMEdXk5PNM69", "155448014.8");
		addGenesisTransaction("QVhWuJkCjStNMV4U8PtNM9Qz4PvLAEtVSj", "101041209.6");
		addGenesisTransaction("QXjNcckFG9gTr9YbiA3RrRhn3mPJ9zyR4G", "3108960.297");
		addGenesisTransaction("QThnuBadmExtxk81vhFKimSzbPaPcuPAdm", "155448014.8");
		addGenesisTransaction("QRc6sQthLHjfkmm2BUhu74g33XtkDoB7JP", "77773983.95");
		addGenesisTransaction("QcDLhirHkSbR4TLYeShLzHw61B8UGTFusk", "23317202.22");
		addGenesisTransaction("QXRnsXE6srHEf2abGh4eogs2mRsmNiuw6V", "5440680.519");
		addGenesisTransaction("QRJmEswbDw4x1kwsLyxtMS9533fv5cDvQV", "3886200.371");
		addGenesisTransaction("Qg43mCzWmFVwhVfx34g6shXnSU7U7amJNx", "6217920.593");
		addGenesisTransaction("QQ9PveFTW64yUcXEE6AxhokWCwhmn8F2TD", "8549640.816");
		addGenesisTransaction("QQaxJuTkW5XXn4DhhRekXpdXaWcsxEfCNG", "3886200.371");
		addGenesisTransaction("QifWFqW8XWL5mcNxtdr5z1LVC7XUu9tNSK", "3116732.697");
		addGenesisTransaction("QavhBKRN4vuyzHNNqcWxjcohRAJNTdTmh4", "154670774.8");
		addGenesisTransaction("QMQyR3Hybof8WpQsXPxh19AZFCj4Z4mmke", "77724007.42");
		addGenesisTransaction("QbT3GGjp1esTXtowVk2XCtBsKoRB8mkP61", "77724007.42");
		addGenesisTransaction("QT13tVMZEtbrgJEsBBcTtnyqGveC7mtqAb", "23317202.22");
		addGenesisTransaction("QegT2Ws5YjLQzEZ9YMzWsAZMBE8cAygHZN", "12606834");
		addGenesisTransaction("QXoKRBJiJGKwvdA3jkmoUhkM7y6vuMp2pn", "65117173.41");
		addGenesisTransaction("QY6SpdBzUev9ziqkmyaxESZSbdKwqGdedn", "89382608.53");
		addGenesisTransaction("QeMxyt1nEE7tbFbioc87xhiKb4szx5DsjY", "15544801.48");
		addGenesisTransaction("QcTp3THGZvJ42f2mWsQrawGvgBoSHgHZyk", "39639243.78");
		addGenesisTransaction("QjSH91mTDN6TeV1naAcfwPhmRogufV4n1u", "23317202.22");
		addGenesisTransaction("QiFLELeLm2TFWsnknzje51wMdt3Srkjz8g", "1554480.148");
		addGenesisTransaction("QhxtJ3vvhsvVU9x2j5n2R3TXzutfLMUvBR", "23317202.22");
		addGenesisTransaction("QUtUSNQfqexZZkaZ2s9LcpqjnTezPTnuAx", "15544801.48");
		addGenesisTransaction("Qg6sPLxNMYxjEDGLLaFkkWx6ip3py5fLEt", "777240.0742");
		addGenesisTransaction("QeLixskYbdkiAHmhBVMa2Pdi85YPFqw3Ed", "38862003.71");
		addGenesisTransaction("Qary17o9qvZ2fifiVC8tF5zoBJm79n18zA", "3893972.772");
		addGenesisTransaction("QLvCWDGwzwpR29XgiThMGDX2vxyFW5rFHB", "8790585.239");
		addGenesisTransaction("Qgc77fSoAoUSVJfq62GxxTin6dBtU7Y6Hb", "194310018.5");
		addGenesisTransaction("QPmPKjwPLCuRei6abuhMtMocxAEeSuLVcv", "23317202.22");
		addGenesisTransaction("QcGfZePUN7JHs9WEEkJhXGzALy4JybiS3N", "194224522.1");
		addGenesisTransaction("QSeXGwk7eQjR8j7bndJST19qWtM2qnqL1u", "38862003.71");
		addGenesisTransaction("QU9i68h71nKTg4gwc5yJHzNRQdQEswP7Kn", "139592317.3");
		addGenesisTransaction("QdKrZGCkwXSSeXJhVA1idDXsA4VFtrjPHN", "15544801.48");
		addGenesisTransaction("QiYJ2B797xFpWYFu4XWivhGhyPXLU7S5Mr", "77724007.42");
		addGenesisTransaction("QWxqtsNXUWSjYns2wdngh4WBSWQzLoQHvx", "232613963.9");
		addGenesisTransaction("QTAGfu4FpTZ1bnvnd17YPtB3zabxfWKNeM", "101041209.6");
		addGenesisTransaction("QPtRxchgRdwdnoZRwhiAoa77AvVPNSRcQk", "114254290.9");
		addGenesisTransaction("QMcfoVc9Jat2pMFLHcuPEPnY6p6uBK6Dk7", "77724007.42");
		addGenesisTransaction("Qi84KosdwSWHZX3qz4WcMgqYGutBmj14dd", "15544801.48");
		addGenesisTransaction("QjAtcHsgig2tvdGr5tR4oGmRarhuojrAK1", "2883560.675");
		addGenesisTransaction("QPJPNLP2NMHu5athB7ydezdTA6zviCV378", "6373368.608");
		addGenesisTransaction("QfVLpmLbuUnA1JEe9FmeUAzihoBvqYDp8B", "15544801.48");
		addGenesisTransaction("QVVFdy6VLFqAFCb6XSBJLLZybiKgfgDDZV", "10725913.02");
		addGenesisTransaction("QVFXyeG1xpAR8Xg3u7oAmW8unueGAfeaKi", "31221733.78");
		addGenesisTransaction("QdtQtrM1h3TLtwAGCNyoTrW5HyiPRLhrPq", "138426457.2");
		addGenesisTransaction("QMukUMr84Mi2niz6rdhEJMkKJBve7uuRfe", "116586011.1");
		addGenesisTransaction("QZR8c7dmfwqGPujebFH1miQToJZ4JQfU1X", "217938116.8");
		addGenesisTransaction("QVV5Uu8eCxufTrBtquDKA96d7Kk8S4V7yX", "40091961.25");
		addGenesisTransaction("QY9YdgfTEUFvQ2UJszGS63qkwdENkW1PQ5", "154670774.8");
		addGenesisTransaction("QNgiswyhVyNJG4UMzvoSf29wDvGZqqs7WG", "11658601.11");
		addGenesisTransaction("QabjgFiY34oihNkUcy9hpFjQdCaypCShMe", "54406805.19");
		addGenesisTransaction("QionidPRekdshCTRL3c7idWWRAqGYcKaFN", "7772400.742");
		addGenesisTransaction("QcJdBJiVgiNBNg6ZwZAiEfYDMi5ZTQaYAa", "81386689.86");
		addGenesisTransaction("QNc8XMpPwM1HESwB7kqw8HoQ5sK2miZ2un", "190423818.2");
		addGenesisTransaction("QUP1SeaNw7CvCnmDp5ai3onWYwThS4GEpu", "3886200.371");
		addGenesisTransaction("QinToqEztNN1TsLdQEuzTHh7vUrEo6JTU2", "102440241.8");
		addGenesisTransaction("QcLJYLV4RD4GmPcoNnh7dQrWeYgiiPiqFQ", "32644083.11");
		addGenesisTransaction("QdYdYGYfgmMX4jQNWMZqLr81R3HdnuiKkv", "76169527.27");
		addGenesisTransaction("Qi62mUW5zfJhgRL8FRmCpjSCCnSKtf76S6", "76169527.27");
		addGenesisTransaction("QgFkxqQGkLW6CD95N2zTnT1PPqb9nxWp6b", "76169527.27");
		addGenesisTransaction("QfNUBudYsrrq27YqiHGLUg6BtG52W1W1ci", "15544801.48");
		addGenesisTransaction("QPSFoexnGoMH7EPdg72dM7SvqA7d4M2cu7", "37307523.56");
		addGenesisTransaction("QQxt5WMvoJ2TNScAzcoxHXPnLTeQ43nQ7N", "21995894.1");
		addGenesisTransaction("QicpACxck2oDYpzP8iWRQYD4oirCtvjok9", "93268808.9");
		addGenesisTransaction("QVTJkdQkTGgqEED9kAsp4BZbYNJqWfhgGw", "153909079.5");
		addGenesisTransaction("QQL5vCkhpXnP9F4wqNiBQsNaCocmRcDSUY", "15512934.64");
		addGenesisTransaction("QSvEex3p2LaZCVBaCyL8MpYsEpHLwed17r", "155448014.8");
		addGenesisTransaction("Qb3Xv96GucQpBG8n96QVFgcs2xXsEWW4CE", "38862003.71");
		addGenesisTransaction("QdRua9MqXufALpQFDeYiQDYk3EBGdwGXSx", "230303229.1");
		addGenesisTransaction("Qh16Umei91JqiHEVWV8AC6ED9aBqbDYuph", "231073474");
		addGenesisTransaction("QMu6HXfZCnwaNmyFjjhWTYAUW7k1x7PoVr", "231073474");
		addGenesisTransaction("QgcphUTiVHHfHg8e1LVgg5jujVES7ZDUTr", "115031531");
		addGenesisTransaction("QbQk9s4j4EAxAguBhmqA8mdtTct3qGnsrx", "138348733.2");
		addGenesisTransaction("QT79PhvBwE6vFzfZ4oh5wdKVsEazZuVJFy", "6360421.343");

		this.transactionsSignature = GENESIS_TRANSACTIONS_SIGNATURE;
	}

	/**
	 * Return cached GenesisBlock to save constructing one from scratch.
	 * 
	 * @return GenesisBlock
	 */
	public static GenesisBlock getInstance() {
		if (instance == null)
			instance = new GenesisBlock();

		return instance;
	}

	// Getters/setters

	// More information

	public static boolean isGenesisBlock(Block block) {
		if (block.height != 1)
			return false;

		// Validate block signature
		if (!Arrays.equals(GENESIS_GENERATION_SIGNATURE, block.generationSignature))
			return false;

		// Validate transactions signature
		if (!Arrays.equals(GENESIS_TRANSACTIONS_SIGNATURE, block.transactionsSignature))
			return false;

		return true;
	}

	// Load/Save

	protected GenesisBlock(byte[] signature) throws SQLException {
		super(signature);
	}

	protected GenesisBlock(ResultSet rs) throws SQLException {
		super(rs);
	}

	// Navigation

	/**
	 * Refuse to load parent of GenesisBlock from DB!
	 * <p>
	 * As the genesis block is the first block, this always returns null.
	 * 
	 * @return null
	 * @throws SQLException
	 */
	@Override
	public Block getParent() throws SQLException {
		return null;
	}

	// Converters

	// Processing

	@Override
	public boolean addTransaction(Transaction transaction) {
		// The genesis block has a fixed set of transactions so always refuse.
		return false;
	}

	private void addGenesisTransaction(String recipient, String amount) {
		this.transactions.add(new GenesisTransaction(recipient, new BigDecimal(amount).setScale(8), this.timestamp));
	}

	/**
	 * Refuse to calculate genesis block signature!
	 * <p>
	 * This is not possible as there is no private key for the genesis account and so no way to sign data.
	 * <p>
	 * <b>Always throws IllegalStateException.</b>
	 * 
	 * @throws IllegalStateException
	 */
	@Override
	public byte[] calcSignature(PrivateKeyAccount signer) {
		throw new IllegalStateException("There is no private key for genesis transactions");
	}

	/**
	 * Generate genesis block generation/transactions signature.
	 * <p>
	 * This is handled differently as there is no private key for the genesis account and so no way to sign data.
	 * <p>
	 * Instead we return the SHA-256 digest of the block, duplicated so that the returned byte[] is the same length as normal block signatures.
	 * 
	 * @return byte[]
	 */
	private static byte[] calcSignature() {
		byte[] digest = Crypto.digest(getBytesForSignature());
		return Bytes.concat(digest, digest);
	}

	private static byte[] getBytesForSignature() {
		try {
			// Passing expected size to ByteArrayOutputStream avoids reallocation when adding more bytes than default 32.
			// See below for explanation of some of the values used to calculated expected size.
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + 64 + GENERATION_TARGET_LENGTH + GENERATOR_LENGTH);
			/*
			 * NOTE: Historic code had genesis block using Longs.toByteArray() compared to standard block's Ints.toByteArray. The subsequent
			 * Bytes.ensureCapacity(versionBytes, 0, 4) did not truncate versionBytes back to 4 bytes either. This means 8 bytes were used even though
			 * VERSION_LENGTH is set to 4. Correcting this historic bug will break genesis block signatures!
			 */
			bytes.write(Longs.toByteArray(GENESIS_BLOCK_VERSION));
			/*
			 * NOTE: Historic code had the reference expanded to only 64 bytes whereas standard block references are 128 bytes. Correcting this historic bug
			 * will break genesis block signatures!
			 */
			bytes.write(Bytes.ensureCapacity(GENESIS_REFERENCE, 64, 0));
			bytes.write(Longs.toByteArray(GENESIS_GENERATION_TARGET.longValue()));
			// NOTE: Genesis account's public key is only 8 bytes, not the usual 32.
			bytes.write(GENESIS_GENERATOR.getPublicKey());
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isSignatureValid() {
		// Validate block signature
		if (!Arrays.equals(GENESIS_GENERATION_SIGNATURE, this.generationSignature))
			return false;

		// Validate transactions signature
		if (!Arrays.equals(GENESIS_TRANSACTIONS_SIGNATURE, this.transactionsSignature))
			return false;

		return true;
	}

	@Override
	public boolean isValid(Connection connection) throws SQLException {
		// Check there is no other block in DB
		if (BlockChain.getMaxHeight() != 0)
			return false;

		// Validate transactions
		for (Transaction transaction : this.getTransactions())
			if (transaction.isValid(connection) != Transaction.ValidationResult.OK)
				return false;

		return true;
	}

}
