package org.qora.crosschain;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.qora.settings.Settings;

public class BTC {

	private static class RollbackBlockChain extends BlockChain {

		public RollbackBlockChain(NetworkParameters params, BlockStore blockStore) throws BlockStoreException {
			super(params, blockStore);
		}

		@Override
		public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
			super.setChainHead(chainHead);
		}

	}

	private static class UpdateableCheckpointManager extends CheckpointManager implements NewBestBlockListener {

		private static final int checkpointInterval = 500;

		private static final String minimalTestNet3TextFile = "TXT CHECKPOINTS 1\n0\n1\nAAAAAAAAB+EH4QfhAAAH4AEAAAApmwX6UCEnJcYIKTa7HO3pFkqqNhAzJVBMdEuGAAAAAPSAvVCBUypCbBW/OqU0oIF7ISF84h2spOqHrFCWN9Zw6r6/T///AB0E5oOO\n";
		private static final String minimalMainNetTextFile = "TXT CHECKPOINTS 1\n0\n1\nAAAAAAAAB+EH4QfhAAAH4AEAAABjl7tqvU/FIcDT9gcbVlA4nwtFUbxAtOawZzBpAAAAAKzkcK7NqciBjI/ldojNKncrWleVSgDfBCCn3VRrbSxXaw5/Sf//AB0z8Bkv\n";

		public UpdateableCheckpointManager(NetworkParameters params) throws IOException {
			super(params, getMinimalTextFileStream(params));
		}

		public UpdateableCheckpointManager(NetworkParameters params, InputStream inputStream) throws IOException {
			super(params, inputStream);
		}

		private static ByteArrayInputStream getMinimalTextFileStream(NetworkParameters params) {
			if (params == MainNetParams.get())
				return new ByteArrayInputStream(minimalMainNetTextFile.getBytes());

			if (params == TestNet3Params.get())
				return new ByteArrayInputStream(minimalTestNet3TextFile.getBytes());

			throw new RuntimeException("Failed to construct empty UpdateableCheckpointManageer");
		}

		@Override
		public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
			int height = block.getHeight();

			if (height % checkpointInterval == 0)
				checkpoints.put(block.getHeader().getTimeSeconds(), block);
		}

		public void saveAsText(File textFile) throws FileNotFoundException {
			try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(textFile), StandardCharsets.US_ASCII))) {
				writer.println("TXT CHECKPOINTS 1");
				writer.println("0"); // Number of signatures to read. Do this later.
				writer.println(checkpoints.size());
				ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
				for (StoredBlock block : checkpoints.values()) {
					block.serializeCompact(buffer);
					writer.println(CheckpointManager.BASE64.encode(buffer.array()));
					buffer.position(0);
				}
			}
		}

		@SuppressWarnings("unused")
		public void saveAsBinary(File file) throws IOException {
			try (final FileOutputStream fileOutputStream = new FileOutputStream(file, false)) {
				MessageDigest digest = Sha256Hash.newDigest();

				try (final DigestOutputStream digestOutputStream = new DigestOutputStream(fileOutputStream, digest)) {
					digestOutputStream.on(false);

					try (final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream)) {
						dataOutputStream.writeBytes("CHECKPOINTS 1");
						dataOutputStream.writeInt(0); // Number of signatures to read. Do this later.
						digestOutputStream.on(true);
						dataOutputStream.writeInt(checkpoints.size());
						ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
						for (StoredBlock block : checkpoints.values()) {
							block.serializeCompact(buffer);
							dataOutputStream.write(buffer.array());
							buffer.position(0);
						}
					}
				}
			}
		}

	}

	private static BTC instance;
	private static final Object instanceLock = new Object();

	private static File directory;
	private static String chainFileName;
	private static String checkpointsFileName;

	private static NetworkParameters params;
	private static PeerGroup peerGroup;
	private static BlockStore blockStore;
	private static RollbackBlockChain chain;
	private static UpdateableCheckpointManager manager;

	private BTC() {
		// Start wallet
		if (Settings.getInstance().useBitcoinTestNet()) {
			params = TestNet3Params.get();
			chainFileName = "bitcoinj-testnet.spvchain";
			checkpointsFileName = "checkpoints-testnet.txt";
		} else {
			params = MainNetParams.get();
			chainFileName = "bitcoinj.spvchain";
			checkpointsFileName = "checkpoints.txt";
		}

		directory = new File("Qora-BTC");
		if (!directory.exists())
			directory.mkdirs();

		File chainFile = new File(directory, chainFileName);

		try {
			blockStore = new SPVBlockStore(params, chainFile);
		} catch (BlockStoreException e) {
			throw new RuntimeException("Failed to open/create BTC SPVBlockStore", e);
		}

		File checkpointsFile = new File(directory, checkpointsFileName);
		try (InputStream checkpointsStream = new FileInputStream(checkpointsFile)) {
			manager = new UpdateableCheckpointManager(params, checkpointsStream);
		} catch (FileNotFoundException e) {
			// Construct with no checkpoints then
			try {
				manager = new UpdateableCheckpointManager(params);
			} catch (IOException e2) {
				throw new RuntimeException("Failed to create new BTC checkpoints", e2);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to load BTC checkpoints", e);
		}

		try {
			chain = new RollbackBlockChain(params, blockStore);
		} catch (BlockStoreException e) {
			throw new RuntimeException("Failed to construct BTC blockchain", e);
		}

		peerGroup = new PeerGroup(params, chain);
		peerGroup.setUserAgent("qqq", "1.0");
		peerGroup.addPeerDiscovery(new DnsDiscovery(params));
		peerGroup.start();
	}

	public static BTC getInstance() {
		if (instance == null)
			synchronized (instanceLock) {
				if (instance == null)
					instance = new BTC();
			}

		return instance;
	}

	public void shutdown() {
		synchronized (instanceLock) {
			if (instance == null)
				return;

			instance = null;
		}

		peerGroup.stop();

		try {
			blockStore.close();
		} catch (BlockStoreException e) {
			// What can we do?
		}
	}

	protected Wallet createEmptyWallet() {
		ECKey dummyKey = new ECKey();

		KeyChainGroup keyChainGroup = new KeyChainGroup(params);
		keyChainGroup.importKeys(dummyKey);

		Wallet wallet = new Wallet(params, keyChainGroup);

		wallet.removeKey(dummyKey);

		return wallet;
	}

	public void watch(String base58Address, long startTime) throws InterruptedException, ExecutionException, TimeoutException, BlockStoreException {
		Wallet wallet = createEmptyWallet();

		WalletCoinsReceivedEventListener coinsReceivedListener = new WalletCoinsReceivedEventListener() {
			@Override
			public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
				System.out.println("Coins received via transaction " + tx.getHashAsString());
			}
		};
		wallet.addCoinsReceivedEventListener(coinsReceivedListener);

		Address address = Address.fromBase58(params, base58Address);
		wallet.addWatchedAddress(address, startTime);

		StoredBlock checkpoint = manager.getCheckpointBefore(startTime);
		blockStore.put(checkpoint);
		blockStore.setChainHead(checkpoint);
		chain.setChainHead(checkpoint);

		chain.addWallet(wallet);
		peerGroup.addWallet(wallet);
		peerGroup.setFastCatchupTimeSecs(startTime);

		System.out.println("Starting download...");
		peerGroup.downloadBlockChain();

		List<TransactionOutput> outputs = wallet.getWatchedOutputs(true);

		peerGroup.removeWallet(wallet);
		chain.removeWallet(wallet);

		for (TransactionOutput output : outputs)
			System.out.println(output.toString());
	}

	public void watch(Script script) {
		// wallet.addWatchedScripts(scripts);
	}

	public void updateCheckpoints() {
		final long now = new Date().getTime() / 1000;

		try {
			StoredBlock checkpoint = manager.getCheckpointBefore(now);
			blockStore.put(checkpoint);
			blockStore.setChainHead(checkpoint);
			chain.setChainHead(checkpoint);
		} catch (BlockStoreException e) {
			throw new RuntimeException("Failed to update BTC checkpoints", e);
		}

		peerGroup.setFastCatchupTimeSecs(now);

		chain.addNewBestBlockListener(Threading.SAME_THREAD, manager);

		peerGroup.downloadBlockChain();

		try {
			manager.saveAsText(new File(directory, checkpointsFileName));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Failed to save updated BTC checkpoints", e);
		}
	}

}
