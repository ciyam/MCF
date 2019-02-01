package org.qora.at;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.ciyam.at.MachineState;
import org.ciyam.at.Timestamp;
import org.qora.account.Account;
import org.qora.block.Block;
import org.qora.data.block.BlockData;
import org.qora.data.transaction.ATTransactionData;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.data.transaction.TransactionData;
import org.qora.repository.BlockRepository;
import org.qora.repository.DataException;
import org.qora.transaction.Transaction;

public enum BlockchainAPI {

	QORA(0) {
		@Override
		public void putTransactionFromRecipientAfterTimestampInA(String recipient, Timestamp timestamp, MachineState state) {
			int height = timestamp.blockHeight;
			int sequence = timestamp.transactionSequence + 1;

			QoraATAPI api = (QoraATAPI) state.getAPI();
			Account recipientAccount = new Account(api.repository, recipient);
			BlockRepository blockRepository = api.repository.getBlockRepository();

			try {
				while (height <= blockRepository.getBlockchainHeight()) {
					BlockData blockData = blockRepository.fromHeight(height);

					if (blockData == null)
						throw new DataException("Unable to fetch block " + height + " from repository?");

					Block block = new Block(api.repository, blockData);

					List<Transaction> transactions = block.getTransactions();

					// No more transactions in this block? Try next block
					if (sequence >= transactions.size()) {
						++height;
						sequence = 0;
						continue;
					}

					Transaction transaction = transactions.get(sequence);

					// Transaction needs to be sent to specified recipient
					if (transaction.getRecipientAccounts().contains(recipientAccount)) {
						// Found a transaction

						api.setA1(state, new Timestamp(height, timestamp.blockchainId, sequence).longValue());

						// Hash transaction's signature into other three A fields for future verification that it's the same transaction
						byte[] hash = QoraATAPI.sha192(transaction.getTransactionData().getSignature());

						api.setA2(state, QoraATAPI.fromBytes(hash, 0));
						api.setA3(state, QoraATAPI.fromBytes(hash, 8));
						api.setA4(state, QoraATAPI.fromBytes(hash, 16));
						return;
					}

					// Transaction wasn't for us - keep going
					++sequence;
				}

				// No more transactions - zero A and exit
				api.zeroA(state);
			} catch (DataException e) {
				throw new RuntimeException("AT API unable to fetch next transaction?", e);
			}
		}

		@Override
		public long getAmountFromTransactionInA(Timestamp timestamp, MachineState state) {
			QoraATAPI api = (QoraATAPI) state.getAPI();
			TransactionData transactionData = api.fetchTransaction(state);

			switch (transactionData.getType()) {
				case PAYMENT:
					return ((PaymentTransactionData) transactionData).getAmount().unscaledValue().longValue();

				case AT:
					BigDecimal amount = ((ATTransactionData) transactionData).getAmount();

					if (amount != null)
						return amount.unscaledValue().longValue();
					else
						return 0xffffffffffffffffL;

				default:
					return 0xffffffffffffffffL;
			}
		}
	},
	BTC(1) {
		@Override
		public void putTransactionFromRecipientAfterTimestampInA(String recipient, Timestamp timestamp, MachineState state) {
			// TODO BTC transaction support for ATv2
		}

		@Override
		public long getAmountFromTransactionInA(Timestamp timestamp, MachineState state) {
			// TODO BTC transaction support for ATv2
			return 0;
		}
	};

	public final int value;

	private final static Map<Integer, BlockchainAPI> map = stream(BlockchainAPI.values()).collect(toMap(type -> type.value, type -> type));

	BlockchainAPI(int value) {
		this.value = value;
	}

	public static BlockchainAPI valueOf(int value) {
		return map.get(value);
	}

	// Blockchain-specific API methods

	public abstract void putTransactionFromRecipientAfterTimestampInA(String recipient, Timestamp timestamp, MachineState state);

	public abstract long getAmountFromTransactionInA(Timestamp timestamp, MachineState state);

}
