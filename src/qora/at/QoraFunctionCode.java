package qora.at;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.ciyam.at.ExecutionException;
import org.ciyam.at.FunctionData;
import org.ciyam.at.IllegalFunctionCodeException;
import org.ciyam.at.MachineState;
import org.ciyam.at.Timestamp;

/**
 * Qora-specific CIYAM-AT Functions.
 * <p>
 * Function codes need to be between 0x0500 and 0x06ff.
 *
 */
public enum QoraFunctionCode {
	/**
	 * <tt>0x0500</tt><br>
	 * Returns current BTC block's "timestamp"
	 */
	GET_BTC_BLOCK_TIMESTAMP(0x0500, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = Timestamp.toLong(state.getAPI().getCurrentBlockHeight(), BlockchainAPI.BTC.value, 0);
		}
	},
	/**
	 * <tt>0x0501</tt><br>
	 * Put transaction from specific recipient after timestamp in A, or zero if none<br>
	 */
	PUT_TX_FROM_B_RECIPIENT_AFTER_TIMESTAMP_IN_A(0x0501, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			Timestamp timestamp = new Timestamp(functionData.value2);

			try {
				String recipient = new String(state.getB(), "UTF-8");

				BlockchainAPI blockchainAPI = BlockchainAPI.valueOf(timestamp.blockchainId);
				blockchainAPI.putTransactionFromRecipientAfterTimestampInA(recipient, timestamp, state);
			} catch (UnsupportedEncodingException e) {
				throw new ExecutionException("Couldn't parse recipient from B", e);
			}
		}
	};

	public final short value;
	public final int paramCount;
	public final boolean returnsValue;

	private final static Map<Short, QoraFunctionCode> map = Arrays.stream(QoraFunctionCode.values())
			.collect(Collectors.toMap(functionCode -> functionCode.value, functionCode -> functionCode));

	private QoraFunctionCode(int value, int paramCount, boolean returnsValue) {
		this.value = (short) value;
		this.paramCount = paramCount;
		this.returnsValue = returnsValue;
	}

	public static QoraFunctionCode valueOf(int value) {
		return map.get((short) value);
	}

	public void preExecuteCheck(int paramCount, boolean returnValueExpected, MachineState state, short rawFunctionCode) throws IllegalFunctionCodeException {
		if (paramCount != this.paramCount)
			throw new IllegalFunctionCodeException(
					"Passed paramCount (" + paramCount + ") does not match function's required paramCount (" + this.paramCount + ")");

		if (returnValueExpected != this.returnsValue)
			throw new IllegalFunctionCodeException(
					"Passed returnValueExpected (" + returnValueExpected + ") does not match function's return signature (" + this.returnsValue + ")");
	}

	/**
	 * Execute Function
	 * <p>
	 * Can modify various fields of <tt>state</tt>, including <tt>programCounter</tt>.
	 * <p>
	 * Throws a subclass of <tt>ExecutionException</tt> on error, e.g. <tt>InvalidAddressException</tt>.
	 *
	 * @param functionData
	 * @param state
	 * @throws ExecutionException
	 */
	public void execute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
		// Check passed functionData against requirements of this function
		preExecuteCheck(functionData.paramCount, functionData.returnValueExpected, state, rawFunctionCode);

		if (functionData.paramCount >= 1 && functionData.value1 == null)
			throw new IllegalFunctionCodeException("Passed value1 is null but function has paramCount of (" + this.paramCount + ")");

		if (functionData.paramCount == 2 && functionData.value2 == null)
			throw new IllegalFunctionCodeException("Passed value2 is null but function has paramCount of (" + this.paramCount + ")");

		state.getLogger().debug("Function \"" + this.name() + "\"");

		postCheckExecute(functionData, state, rawFunctionCode);
	}

	/** Actually execute function */
	abstract protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException;

}
