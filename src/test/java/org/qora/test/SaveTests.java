package org.qora.test;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.Test;
import org.qora.account.PublicKeyAccount;
import org.qora.data.transaction.PaymentTransactionData;
import org.qora.group.Group;
import org.qora.repository.DataException;
import org.qora.repository.Repository;
import org.qora.repository.RepositoryManager;
import org.qora.test.common.Common;
import org.qora.utils.Base58;

public class SaveTests extends Common {

	@Test
	public void testSavePaymentTransaction() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			String reference58 = "rrrr";
			byte[] reference = Base58.decode(reference58);
			String signature58 = "ssss";
			byte[] signature = Base58.decode(signature58);
			PublicKeyAccount sender = new PublicKeyAccount(repository, "Qsender".getBytes());

			PaymentTransactionData paymentTransactionData = new PaymentTransactionData(Instant.now().getEpochSecond(), Group.NO_GROUP, reference,
					sender.getPublicKey(), "Qrecipient", BigDecimal.valueOf(12345L), BigDecimal.ONE, signature);

			repository.getTransactionRepository().save(paymentTransactionData);

			repository.discardChanges();
		}
	}

}
