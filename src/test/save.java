package test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;

import org.junit.Test;

import qora.account.PublicKeyAccount;
import qora.transaction.PaymentTransaction;
import utils.Base58;

public class save extends common {

	@Test
	public void testSavePaymentTransaction() throws SQLException {
		String reference58 = "rrrr";
		byte[] reference = Base58.decode(reference58);
		String signature58 = "ssss";
		byte[] signature = Base58.decode(signature58);
		PublicKeyAccount sender = new PublicKeyAccount("Qsender".getBytes());

		PaymentTransaction paymentTransaction = new PaymentTransaction(sender, "Qrecipient", BigDecimal.valueOf(12345L), BigDecimal.ONE,
				Instant.now().getEpochSecond(), reference, signature);

		paymentTransaction.save();
	}

}
