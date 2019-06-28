package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import qora.account.PrivateKeyAccount;
import qora.crypto.Crypto;
import utils.BIP39;
import utils.Base58;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

@Path("/utils")
@Produces({
	MediaType.TEXT_PLAIN
})
@Tag(
	name = "Utilities"
)
public class UtilsResource {

	@Context
	HttpServletRequest request;

	@POST
	@Path("/fromBase64")
	@Operation(
		summary = "Convert base64 data to hex",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "hex string",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String fromBase64(String base64) {
		try {
			return HashCode.fromBytes(Base64.getDecoder().decode(base64.trim())).toString();
		} catch (IllegalArgumentException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}
	}

	@POST
	@Path("/fromBase58")
	@Operation(
		summary = "Convert base58 data to hex",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "hex string",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String base64from58(String base58) {
		try {
			return HashCode.fromBytes(Base58.decode(base58.trim())).toString();
		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}
	}

	@GET
	@Path("/toBase64/{hex}")
	@Operation(
		summary = "Convert hex to base64",
		responses = {
			@ApiResponse(
				description = "base64",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String toBase64(@PathParam("hex") String hex) {
		return Base64.getEncoder().encodeToString(HashCode.fromString(hex).asBytes());
	}

	@GET
	@Path("/toBase58/{hex}")
	@Operation(
		summary = "Convert hex to base58",
		responses = {
			@ApiResponse(
				description = "base58",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String toBase58(@PathParam("hex") String hex) {
		return Base58.encode(HashCode.fromString(hex).asBytes());
	}

	@GET
	@Path("/random")
	@Operation(
		summary = "Generate random data",
		description = "Optionally pass data length, defaults to 32 bytes.",
		responses = {
			@ApiResponse(
				description = "base58 data",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String random(@QueryParam("length") Integer length) {
		if (length == null)
			length = 32;

		byte[] random = new byte[length];
		new SecureRandom().nextBytes(random);
		return Base58.encode(random);
	}

	@GET
	@Path("/mnemonic")
	@Operation(
		summary = "Generate 12-word BIP39 mnemonic",
		description = "Optionally pass 16-byte, base58-encoded entropy or entropy will be internally generated.<br>"
				+ "Example entropy input: YcVfxkQb6JRzqk5kF2tNLv",
		responses = {
			@ApiResponse(
				description = "mnemonic",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String getMnemonic(@QueryParam("entropy") String suppliedEntropy) {
		/*
		 * BIP39 word lists have 2048 entries so can be represented by 11 bits.
		 * UUID (128bits) and another 4 bits gives 132 bits.
		 * 132 bits, divided by 11, gives 12 words.
		 */
		byte[] entropy;
		if (suppliedEntropy != null) {
			// Use caller-supplied entropy input
			try {
				entropy = Base58.decode(suppliedEntropy);
			} catch (NumberFormatException e) {
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
			}

			// Must be 16-bytes
			if (entropy.length != 16)
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		} else {
			// Generate entropy internally
			UUID uuid = UUID.randomUUID();

			byte[] uuidMSB = Longs.toByteArray(uuid.getMostSignificantBits());
			byte[] uuidLSB = Longs.toByteArray(uuid.getLeastSignificantBits());
			entropy = Bytes.concat(uuidMSB, uuidLSB);
		}

		// Use SHA256 to generate more bits
		byte[] hash = Crypto.digest(entropy);

		// Append first 4 bits from hash to end. (Actually 8 bits but we only use 4).
		byte checksum = (byte) (hash[0] & 0xf0);
		entropy = Bytes.concat(entropy, new byte[] {
			checksum
		});

		return BIP39.encode(entropy, "en");
	}

	@POST
	@Path("/mnemonic")
	@Operation(
		summary = "Calculate binary entropy from 12-word BIP39 mnemonic",
		description = "Returns the base58-encoded binary form, or \"false\" if mnemonic is invalid.",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "entropy in base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String fromMnemonic(String mnemonic) {
		if (mnemonic.isEmpty())
			return "false";

		// Strip leading/trailing whitespace if any
		mnemonic = mnemonic.trim();

		String[] phraseWords = mnemonic.split(" ");
		if (phraseWords.length != 12)
			return "false";

		// Convert BIP39 mnemonic to binary
		byte[] binary = BIP39.decode(phraseWords, "en");
		if (binary == null)
			return "false";

		byte[] entropy = Arrays.copyOf(binary, 16); // 132 bits is 16.5 bytes, but we're discarding checksum nybble

		byte checksumNybble = (byte) (binary[16] & 0xf0);
		byte[] checksum = Crypto.digest(entropy);
		if ((checksum[0] & 0xf0) != checksumNybble)
			return "false";

		return Base58.encode(entropy);
	}

	@GET
	@Path("/privateKey/{entropy}")
	@Operation(
		summary = "Calculate private key from supplied 16-byte entropy",
		responses = {
			@ApiResponse(
				description = "private key in base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String privateKey(@PathParam("entropy") String entropy58) {
		byte[] entropy;
		try {
			entropy = Base58.decode(entropy58);
		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}

		if (entropy.length != 16)
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);

		byte[] privateKey = Crypto.digest(entropy);

		return Base58.encode(privateKey);
	}

	@GET
	@Path("/publicKey/{privateKey}")
	@Operation(
		summary = "Calculate public key from supplied 32-byte private key",
		responses = {
			@ApiResponse(
				description = "public key in base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String publicKey(@PathParam("privateKey") String privateKey58) {
		byte[] privateKey;
		try {
			privateKey = Base58.decode(privateKey58);
		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}

		if (privateKey.length != 32)
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);

		byte[] publicKey = new PrivateKeyAccount(null, privateKey).getPublicKey();

		return Base58.encode(publicKey);
	}

}