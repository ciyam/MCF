package test;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.common.hash.HashCode;

import data.transaction.TransactionData;
import qora.transaction.CreateOrderTransaction;
import qora.transaction.CreatePollTransaction;
import qora.transaction.IssueAssetTransaction;
import transform.TransformationException;
import transform.transaction.TransactionTransformer;

public class CompatibilityTests {

	@Test
	public void testCreateOrderTransactionSignature() throws TransformationException {
		// 4EsGzQ87rXqXw2nic8LiihGCrM5iNErK53u9TRo2AJv4FWWyCK7bUKrCmswnrBbkB7Dsk7wfzi9hM2TGGqm6LVpd
		byte[] rawTx = HashCode
				.fromString("0000000d" + "000001489be3ef8e"
						+ "10b52b229c73afb40a56df4f1c9f65072041011cf9ae25a053397d9fc5578bc8f1412eb404de4e318e24302863fc52889eb848af65a6b17cfc964267388f5802"
						+ "bf497fa72ed16894f3acab6c4a101fd8b5fd42f0420dad45474388d5492d38d0" + "0000000000000000" + "0000000000000001"
						+ "000000000000000005f5e100" + "000000000000000005f5e100" + "0000000005f5e100"
						+ "a2025bfde5c90254e16150db6aef6189bb2856df51940b6a15b1d5f174451236062c982af4da3429941337abc7002a862782fb9c726bfc95aea31e30bf66a502")
				.asBytes();

		TransactionData transactionData = TransactionTransformer.fromBytes(rawTx);

		CreateOrderTransaction transaction = new CreateOrderTransaction(null, transactionData);
		assertTrue(transaction.isSignatureValid());
	}

	@Test
	public void testCreatePollTransactionSignature() throws TransformationException {
		// 5xo8YxDVTFVR1pdmtxYkRbq3PkcKVttyH7wCVAfgqokDMKE1XW6zrqFgJG8vRQz9qi5r8cqBoSgFKLnQRoSyzpgF
		byte[] rawTx = HashCode
				.fromString("00000008" + "00000146d4237f03"
						+ "c201817ee2d4363801b63cbe154f6796719feb5a9673758dfda7b5e616cddd1263bbb75ce6a14ca116abe2d34ea68f353379d0c0d48da62180677053792f3b00"
						+ "ef893a99782612754157d868fc4194577cca8ca5dd264c90855829f0e4bbec3a" + "3a91655f3c70d7a38980b449ccf7acd84de41f99dae6215ed5" + "0000000a"
						+ "746869736973706f6c6c" + "00000004" + "74657374" + "00000002" + "00000011" + "546869732069732073706f6e6765626f62" + "00000000"
						+ "0000000f" + "54686973206973207061747269636b" + "00000000" + "0000000005f5e100"
						+ "f82f0c7421333c2cae5d0d0200e7f4726cda60baecad4ba067c7da17c681e2fb20612991f75763791b228c258f79ec2ecc40788fdda71b8f11a9032417ec7e08")
				.asBytes();

		TransactionData transactionData = TransactionTransformer.fromBytes(rawTx);

		CreatePollTransaction transaction = new CreatePollTransaction(null, transactionData);
		assertTrue(transaction.isSignatureValid());
	}

	@Test
	public void testIssueAssetTransactionSignature() throws TransformationException {
		// 3JeJ8yGnG8RCQH51S2qYJT5nfbokjHnBmM7KZsj61HPRy8K3ZWkGHh99QQ6HbRHxnknnjjAsffHRaeca1ap3tcFv
		byte[] rawTx = HashCode
				.fromString(
						"0000000b000001489376bea34d4cbdb644be00b5848a2beeee087fdb98de49a010e686de9540f7d83720cdd182ca6efd1a6225f72f2821ed8a19f236002aef33afa4e2e419fe641c2bc4800a8dd3440f3ce0526c924f2cc15f3fdc1afcf4d57e4502c7a13bfed9851e81abc93a6a24ae1a453205b39d0c3bd24fb5eb675cd199e7cb5b316c00000003787878000000117878787878787878787878787878787878000000000000006400733fa8fa762c404ca1ddd799e93cc8ea292cd9fdd84d5c8b094050d4f576ea56071055f9fe337bf610624514f673e66462f8719759242b5635f19da088b311050000000005f5e100733fa8fa762c404ca1ddd799e93cc8ea292cd9fdd84d5c8b094050d4f576ea56071055f9fe337bf610624514f673e66462f8719759242b5635f19da088b31105")
				.asBytes();

		TransactionData transactionData = TransactionTransformer.fromBytes(rawTx);

		IssueAssetTransaction transaction = new IssueAssetTransaction(null, transactionData);
		assertTrue(transaction.isSignatureValid());
	}

}
