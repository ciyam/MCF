package org.qora.test;

import org.junit.Test;
import org.qora.asset.Asset;
import org.qora.data.at.ATStateData;
import org.qora.data.block.BlockData;
import org.qora.data.block.BlockTransactionData;
import org.qora.data.transaction.DeployAtTransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.transaction.DeployAtTransaction;
import org.qora.transform.TransformationException;
import org.qora.utils.Base58;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;

import com.google.common.hash.HashCode;

public class ATTests extends Common {

	@Test
	public void testATAccount() throws TransformationException, DataException {
		// 2dZ4megUyNoYYY7qWmuSd4xw1yUKgPPF97yBbeddh8aKuC8PLpz7Xvf3r6Zjv1zwGrR8fEAHuaztCPD4KQp76KdL at height 125598
		// AT address: AaaUn82XV4YcUtsQ3rHa5ZgqyiK35rVfE3

		String expectedAddress = "AaaUn82XV4YcUtsQ3rHa5ZgqyiK35rVfE3";

		byte[] creatorPublicKey = HashCode.fromString("c74d71ecec6b37890f26573186e634986cc90a507af01749f92aa2c7c95ad05f").asBytes();
		String name = "QORABURST @ 1.00";
		String description = "Initiators BURST address: BURST-LKGW-Z2JK-EZ99-E7CUE";
		String ATType = "acct";
		String tags = "acct,atomic cross chain tx,initiate,initiator";
		byte[] creationBytes = HashCode
				.fromString("010000000100010000000000" + "0094357700" + "000000bf"
						+ "3501030900000006040000000900000029302009000000040000000f1ab4000000330403090000003525010a000000260a000000320903350703090000003526010a0000001b0a000000cd322801331601000000003317010100000033180102000000331901030000003505020a0000001b0a000000a1320b033205041e050000001833000509000000320a033203041ab400000033160105000000331701060000003318010700000033190108000000320304320b033203041ab7"
						+ "00000048"
						+ "5e211280259d2f3130248482c2dfc53be2fd5f9bedc9bc21425f951e8097a21900000000c80000003ac8716ad810191acf270d22e9f47f27806256c10d6ba6144900000000000000")
				.asBytes();
		BigDecimal amount = BigDecimal.valueOf(500.0).setScale(8);
		BigDecimal fee = BigDecimal.valueOf(20.0).setScale(8);
		long timestamp = 1439997077932L;
		byte[] reference = Base58.decode("2D3jX1pEgu6irsQ7QzJb85QP1D9M45dNyP5M9a3WFHndU5ZywF4F5pnUurcbzMnGMcTwpAY6H7DuLw8cUBU66ao1");
		byte[] signature = Base58.decode("2dZ4megUyNoYYY7qWmuSd4xw1yUKgPPF97yBbeddh8aKuC8PLpz7Xvf3r6Zjv1zwGrR8fEAHuaztCPD4KQp76KdL");

		DeployAtTransactionData transactionData = new DeployAtTransactionData(timestamp, Group.NO_GROUP, reference, creatorPublicKey, name, description, ATType,
				tags, creationBytes, amount, Asset.QORA, fee, signature);

		try (final Repository repository = RepositoryManager.getRepository()) {
			repository.getTransactionRepository().save(transactionData);

			DeployAtTransaction transaction = new DeployAtTransaction(repository, transactionData);

			// Fake entry for this transaction at block height 125598 if it doesn't already exist
			if (transaction.getHeight() == 0) {
				byte[] blockSignature = Base58.decode(
						"2amu634LnAbxeLfDtWdTLiCWtKu1XM2XLK9o6fDM7yGNNoh5Tq2KxSLdx8AS486zUU1wYNGCm8mcGxjMiww979MxdPVB2PQzaKrW2aFn9hpdSNN6Nk7EmeYKwsZdx9tkpHfBt5thSrUUrhzXJju9KYCAP6p3Ty4zccFkaxCP15j332U");
				byte[] generatorSignature = Arrays.copyOfRange(blockSignature, 0, 64);
				byte[] transactionsSignature = Arrays.copyOfRange(blockSignature, 64, 128);

				// Check block exists too
				if (repository.getBlockRepository().fromSignature(blockSignature) == null) {
					int version = 2;
					byte[] blockReference = blockSignature;
					int transactionCount = 0;
					BigDecimal totalFees = BigDecimal.valueOf(70.0).setScale(8);
					int height = 125598;
					long blockTimestamp = 1439997158336L;
					BigDecimal generatingBalance = BigDecimal.valueOf(1440368826L).setScale(8);
					byte[] generatorPublicKey = Base58.decode("X4s833bbtghh7gejmaBMbWqD44HrUobw93ANUuaNhFc");
					int atCount = 1;
					BigDecimal atFees = BigDecimal.valueOf(50.0).setScale(8);

					BlockData blockData = new BlockData(version, blockReference, transactionCount, totalFees, transactionsSignature, height, blockTimestamp,
							generatingBalance, generatorPublicKey, generatorSignature, atCount, atFees);

					repository.getBlockRepository().save(blockData);

					byte[] atBytes = HashCode.fromString("17950a6c62d17ff0caa545651c054a105f1c464daca443df846cc6a3d58f764b78c09cff50f0fd9ec2").asBytes();

					String atAddress = Base58.encode(Arrays.copyOfRange(atBytes, 0, 25));
					byte[] stateHash = Arrays.copyOfRange(atBytes, 25, atBytes.length);

					ATStateData atStateData = new ATStateData(atAddress, height, timestamp, new byte[0], stateHash, atFees);

					repository.getATRepository().save(atStateData);
				}

				int sequence = 0;

				BlockTransactionData blockTransactionData = new BlockTransactionData(blockSignature, sequence, signature);
				repository.getBlockRepository().save(blockTransactionData);
			}

			String actualAddress = transaction.getATAccount().getAddress();

			repository.discardChanges();

			assertEquals(expectedAddress, actualAddress);
		}
	}

}
