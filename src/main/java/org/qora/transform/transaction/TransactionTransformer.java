package org.qora.transform.transaction;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.qora.account.PrivateKeyAccount;
import org.qora.data.transaction.TransactionData;
import org.qora.transaction.Transaction;
import org.qora.transaction.Transaction.TransactionType;
import org.qora.transform.TransformationException;
import org.qora.transform.Transformer;
import org.qora.utils.Base58;

import com.google.common.hash.HashCode;

public abstract class TransactionTransformer extends Transformer {

	private static final Logger LOGGER = LogManager.getLogger(TransactionTransformer.class);

	protected static final int TYPE_LENGTH = INT_LENGTH;
	protected static final int REFERENCE_LENGTH = SIGNATURE_LENGTH;
	protected static final int FEE_LENGTH = BIG_DECIMAL_LENGTH;
	protected static final int BASE_TYPELESS_LENGTH = TIMESTAMP_LENGTH + REFERENCE_LENGTH + FEE_LENGTH + SIGNATURE_LENGTH;

	/** Description of one component of raw transaction layout */
	public enum TransformationType {
		TIMESTAMP("milliseconds (long)", TIMESTAMP_LENGTH),
		SIGNATURE("transaction signature", SIGNATURE_LENGTH),
		PUBLIC_KEY("public key", PUBLIC_KEY_LENGTH),
		ADDRESS("address", ADDRESS_LENGTH),
		AMOUNT("amount", BIG_DECIMAL_LENGTH),
		ASSET_QUANTITY("asset-related quantity", 12),
		INT("int", INT_LENGTH),
		LONG("long", LONG_LENGTH),
		STRING("UTF-8 string of variable length", null),
		DATA("opaque data of variable length", null),
		BOOLEAN("0 for false, anything else for true", 1);

		public final String description;
		public final Integer length;

		TransformationType(String description, Integer length) {
			this.description = description;
			this.length = length;
		}
	}

	/** Description of one component of raw transaction layout for API use */
	@XmlAccessorType(XmlAccessType.NONE)
	public static class Transformation {
		@XmlElement
		public String description;
		public TransformationType transformation;

		protected Transformation() {
		}

		public Transformation(String description, TransformationType format) {
			this.description = description;
			this.transformation = format;
		}

		@XmlElement(
			name = "format"
		)
		public String getFormat() {
			return this.transformation.description;
		}

		@XmlElement(
			name = "length"
		)
		public Integer getLength() {
			return this.transformation.length;
		}
	}

	/** Container for raw transaction layout */
	public static class TransactionLayout {
		private List<Transformation> layout = new ArrayList<>();

		public void add(String name, TransformationType format) {
			layout.add(new Transformation(name, format));
		}

		public List<Transformation> getLayout() {
			return this.layout;
		}
	}

	/** Container for cache of transformer subclass reflection info */
	public static class TransformerSubclassInfo {
		public Class<?> clazz;
		public TransactionLayout transactionLayout;
		public Method fromByteBufferMethod;
		public Method getDataLengthMethod;
		public Method toBytesMethod;
		public Method toBytesForSigningImplMethod;
		public Method toJSONMethod;
	}

	/** Cache of transformer subclass info, keyed by transaction type */
	private static final TransformerSubclassInfo[] subclassInfos;
	static {
		subclassInfos = new TransformerSubclassInfo[TransactionType.values().length + 1];

		for (TransactionType txType : TransactionType.values()) {
			TransformerSubclassInfo subclassInfo = new TransformerSubclassInfo();

			try {
				subclassInfo.clazz = Class
						.forName(String.join("", TransactionTransformer.class.getPackage().getName(), ".", txType.className, "TransactionTransformer"));
			} catch (ClassNotFoundException e) {
				LOGGER.debug(String.format("TransactionTransformer subclass not found for transaction type \"%s\"", txType.name()));
				continue;
			}

			try {
				Field layoutField = subclassInfo.clazz.getDeclaredField("layout");
				subclassInfo.transactionLayout = ((TransactionLayout) layoutField.get(null));
			} catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
				LOGGER.debug(String.format("TransactionTransformer subclass's \"layout\" field not found for transaction type \"%s\"", txType.name()));
			}

			try {
				subclassInfo.fromByteBufferMethod = subclassInfo.clazz.getDeclaredMethod("fromByteBuffer", ByteBuffer.class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("TransactionTransformer subclass's \"fromByteBuffer\" method not found for transaction type \"%s\"", txType.name()));
			}

			try {
				subclassInfo.getDataLengthMethod = subclassInfo.clazz.getDeclaredMethod("getDataLength", TransactionData.class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("TransactionTransformer subclass's \"getDataLength\" method not found for transaction type \"%s\"", txType.name()));
			}

			try {
				subclassInfo.toBytesMethod = subclassInfo.clazz.getDeclaredMethod("toBytes", TransactionData.class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("TransactionTransformer subclass's \"toBytes\" method not found for transaction type \"%s\"", txType.name()));
			}

			try {
				Class<?> transformerClass = subclassInfo.clazz;

				// Check method is actually declared in transformer, otherwise we have to call superclass version
				if (Arrays.asList(transformerClass.getDeclaredMethods()).stream().noneMatch(method -> method.getName().equals("toBytesForSigningImpl")))
					transformerClass = transformerClass.getSuperclass();

				subclassInfo.toBytesForSigningImplMethod = transformerClass.getDeclaredMethod("toBytesForSigningImpl", TransactionData.class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("TransactionTransformer subclass's \"toBytesFromSigningImp\" method not found for transaction type \"%s\"",
						txType.name()));
			}

			try {
				subclassInfo.toJSONMethod = subclassInfo.clazz.getDeclaredMethod("toJSON", TransactionData.class);
			} catch (IllegalArgumentException | SecurityException | NoSuchMethodException e) {
				LOGGER.debug(String.format("TransactionTransformer subclass's \"toJSON\" method not found for transaction type \"%s\"", txType.name()));
			}

			subclassInfos[txType.value] = subclassInfo;
		}

		LOGGER.trace("Static init reflection completed");
	}

	public static List<Transformation> getLayoutByTxType(TransactionType txType) {
		try {
			return subclassInfos[txType.value].transactionLayout.getLayout();
		} catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
			return null;
		}
	}

	public static TransactionData fromBytes(byte[] bytes) throws TransformationException {
		if (bytes == null)
			return null;

		if (bytes.length < TYPE_LENGTH)
			throw new TransformationException("Byte data too short to determine transaction type");

		LOGGER.trace("tx hex: " + HashCode.fromBytes(bytes).toString());

		ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

		TransactionType type = TransactionType.valueOf(byteBuffer.getInt());
		if (type == null)
			return null;

		Method method = subclassInfos[type.value].fromByteBufferMethod;
		if (method == null)
			throw new TransformationException("Unsupported transaction type [" + type.value + "] during conversion from bytes");

		try {
			return (TransactionData) method.invoke(null, byteBuffer);
		} catch (BufferUnderflowException e) {
			throw new TransformationException("Byte data too short for transaction type [" + type.value + "]");
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new TransformationException("Internal error with transaction type [" + type.value + "] during conversion from bytes");
		}
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		TransactionType type = transactionData.getType();

		try {
			Method method = subclassInfos[type.value].getDataLengthMethod;
			return (int) method.invoke(null, transactionData);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new TransformationException("Internal error with transaction type [" + type.value + "] when requesting byte length");
		}
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		TransactionType type = transactionData.getType();

		try {
			Method method = subclassInfos[type.value].toBytesMethod;
			return (byte[]) method.invoke(null, transactionData);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new TransformationException("Internal error with transaction type [" + type.value + "] during conversion to bytes");
		}
	}

	/**
	 * Serialize transaction as byte[], stripping off trailing signature ready for signing/verification.
	 * <p>
	 * Used by signature-related methods such as {@link Transaction#sign(PrivateKeyAccount)} and {@link Transaction#isSignatureValid()}
	 * 
	 * @param transactionData
	 * @return byte[] of transaction, without trailing signature
	 * @throws TransformationException
	 */
	public static byte[] toBytesForSigning(TransactionData transactionData) throws TransformationException {
		TransactionType type = transactionData.getType();

		try {
			Method method = subclassInfos[type.value].toBytesForSigningImplMethod;
			return (byte[]) method.invoke(null, transactionData);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new TransformationException("Internal error with transaction type [" + type.value + "] during conversion to bytes for signing");
		}
	}

	/**
	 * Generic serialization of transaction as byte[], stripping off trailing signature ready for signing/verification.
	 * 
	 * @param transactionData
	 * @return byte[] of transaction, without trailing signature
	 * @throws TransformationException
	 */
	protected static byte[] toBytesForSigningImpl(TransactionData transactionData) throws TransformationException {
		try {
			byte[] bytes = TransactionTransformer.toBytes(transactionData);

			if (transactionData.getSignature() == null)
				return bytes;

			return Arrays.copyOf(bytes, bytes.length - Transformer.SIGNATURE_LENGTH);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for signing", e);
		}
	}

	public static JSONObject toJSON(TransactionData transactionData) throws TransformationException {
		TransactionType type = transactionData.getType();

		try {
			Method method = subclassInfos[type.value].toJSONMethod;
			return (JSONObject) method.invoke(null, transactionData);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new TransformationException("Internal error with transaction type [" + type.value + "] during conversion to JSON");
		}
	}

	@SuppressWarnings("unchecked")
	static JSONObject getBaseJSON(TransactionData transaction) {
		JSONObject json = new JSONObject();

		json.put("type", transaction.getType().value);
		json.put("fee", transaction.getFee().toPlainString());
		json.put("timestamp", transaction.getTimestamp());
		json.put("signature", Base58.encode(transaction.getSignature()));

		byte[] reference = transaction.getReference();
		if (reference != null)
			json.put("reference", Base58.encode(reference));

		// XXX Can't do this as it requires database access:
		// json.put("confirmations", RepositoryManager.getTransactionRepository.getConfirmations(transaction));

		return json;
	}

}
