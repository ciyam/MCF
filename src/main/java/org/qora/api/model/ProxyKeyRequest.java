package org.qora.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class ProxyKeyRequest {

	@Schema(example = "private_key")
	public byte[] generatorPrivateKey;

	@Schema(example = "public_key")
	public byte[] recipientPublicKey;

	public ProxyKeyRequest() {
	}

}
