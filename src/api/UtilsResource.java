package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import qora.crypto.Crypto;
import utils.BIP39;
import utils.Base58;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import globalization.BIP39WordList;

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
	@Path("/base58from64")
	@Operation(
		summary = "Convert base64 data to base58",
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
				description = "base58 data",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String base58from64(String base64) {
		try {
			return Base58.encode(Base64.getDecoder().decode(base64.trim()));
		} catch (IllegalArgumentException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}
	}

	@POST
	@Path("/base64from58")
	@Operation(
		summary = "Convert base58 data to base64",
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
				description = "base64 data",
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
			return Base64.getEncoder().encodeToString(Base58.decode(base58.trim()));
		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}
	}

	@GET
	@Path("/seed")
	@Operation(
		summary = "Generate random seed",
		description = "Optionally pass seed length, defaults to 32 bytes.",
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
	public String seed(@QueryParam("length") Integer length) {
		if (length == null)
			length = 32;

		byte[] seed = new byte[length];
		new SecureRandom().nextBytes(seed);
		return Base58.encode(seed);
	}

	@GET
	@Path("/seedPhrase")
	@Operation(
		summary = "Generate random 12-word BIP39 seed phrase",
		description = "Optionally pass 16-byte, base58-encoded entropy input or entropy will be internally generated.<br>"
				+ "Example entropy input: YcVfxkQb6JRzqk5kF2tNLv",
		responses = {
			@ApiResponse(
				description = "seed phrase",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String seedPhrase(@QueryParam("entropy") String input) {
		/*
		 * BIP39 word lists have 2048 entries so can be represented by 11 bits.
		 * UUID (128bits) and another 4 bits gives 132 bits.
		 * 132 bits, divided by 11, gives 12 words.
		 */
		final int BITS_PER_WORD = 11;

		byte[] message;
		if (input != null) {
			// Use caller-supplied entropy input
			try {
				message = Base58.decode(input);
			} catch (NumberFormatException e) {
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
			}

			// Must be 16-bytes
			if (message.length != 16)
				throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		} else {
			// Generate entropy internally
			UUID uuid = UUID.randomUUID();

			byte[] uuidMSB = Longs.toByteArray(uuid.getMostSignificantBits());
			byte[] uuidLSB = Longs.toByteArray(uuid.getLeastSignificantBits());
			message = Bytes.concat(uuidMSB, uuidLSB);
		}

		// Use SHA256 to generate more bits
		byte[] hash = Crypto.digest(message);

		// Append first 4 bits from hash to end. (Actually 8 bits but we only use 4).
		byte checksum = (byte) (hash[0] & 0xf0);
		message = Bytes.concat(message, new byte[] {
			checksum
		});

		return BIP39.encode(message, "en");
	}

	@POST
	@Path("/seedPhrase")
	@Operation(
		summary = "Calculate binary form of 12-word BIP39 seed phrase",
		description = "Returns the base58-encoded binary form, or \"false\" if phrase is invalid.",
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
				description = "the private key",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	public String getBinarySeed(String seedPhrase) {
		if (seedPhrase.isEmpty())
			return "false";

		// Strip leading/trailing whitespace if any
		seedPhrase = seedPhrase.trim();

		String[] phraseWords = seedPhrase.split(" ");
		if (phraseWords.length != 12)
			return "false";

		// Convert BIP39 seed phrase to binary
		byte[] binary = BIP39.decode(phraseWords, "en");
		if (binary == null)
			return "false";

		byte[] message = Arrays.copyOf(binary, 16); // 132 bits is 16.5 bytes, but we're discarding checksum nybble

		return Base58.encode(message);
	}

}
