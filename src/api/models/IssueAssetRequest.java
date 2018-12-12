package api.models;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

public class IssueAssetRequest {

	@Schema(description = "asset issuer's public key")
	public byte[] issuer;

	@Schema(description = "asset name - must be lowercase", example = "my-asset123")
	public String name;

	@Schema(description = "asset description")
	public String description;

	public BigDecimal quantity;

	public boolean isDivisible;

	public BigDecimal fee;

	public byte[] reference;

}
