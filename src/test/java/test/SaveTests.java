package test;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import data.transaction.PaymentTransactionData;
import qora.account.PublicKeyAccount;
import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;
import utils.Base58;

public class SaveTests extends Common {

	@Test
	public void testSavePaymentTransaction() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			String reference58 = "rrrr";
			byte[] reference = Base58.decode(reference58);
			String signature58 = "ssss";
			byte[] signature = Base58.decode(signature58);
			PublicKeyAccount sender = new PublicKeyAccount(repository, "Qsender".getBytes());

			PaymentTransactionData paymentTransactionData = new PaymentTransactionData(sender.getPublicKey(), "Qrecipient", BigDecimal.valueOf(12345L),
					BigDecimal.ONE, Instant.now().getEpochSecond(), reference, signature);

			repository.getTransactionRepository().save(paymentTransactionData);

			repository.discardChanges();
		}
	}

}
