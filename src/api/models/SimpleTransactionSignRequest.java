package api.models;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class SimpleTransactionSignRequest {

	@Schema(
		description = "signer's private key"
	)
	public byte[] privateKey;

	@Schema(
		description = "raw, unsigned transaction bytes"
	)
	public byte[] transactionBytes;

}
