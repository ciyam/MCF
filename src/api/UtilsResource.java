package api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import qora.crypto.Crypto;
import utils.Base58;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

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

	@GET
	@Path("/base58from64/{base64}")
	@Operation(
		summary = "Convert base64 data to base58",
		responses = {
			@ApiResponse(
				description = "base58 data",
				content = @Content(
					schema = @Schema(
						implementation = String.class
					)
				)
			)
		}
	)
	public String base58from64(@PathParam("base64") String base64) {
		try {
			return Base58.encode(Base64.getDecoder().decode(base64));
		} catch (IllegalArgumentException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}
	}

	@GET
	@Path("/base64from58/{base58}")
	@Operation(
		summary = "Convert base58 data to base64",
		responses = {
			@ApiResponse(
				description = "base64 data",
				content = @Content(
					schema = @Schema(
						implementation = String.class
					)
				)
			)
		}
	)
	public String base64from58(@PathParam("base58") String base58) {
		try {
			return Base64.getEncoder().encodeToString(Base58.decode(base58));
		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(ApiError.INVALID_DATA);
		}
	}

	@GET
	@Path("/seed")
	@Operation(
		summary = "Generate random 32-byte seed",
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
	public String seed() {
		byte[] seed = new byte[32];
		new SecureRandom().nextBytes(seed);
		return Base58.encode(seed);
	}

	@GET
	@Path("/seedPhrase")
	@Operation(
		summary = "Generate random 12-word BIP39 seed phrase",
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
	public String seedPhrase() {
		/*
		 * BIP39 word lists have 2048 entries so can be represented by 11 bits.
		 * UUID (128bits) and another 4 bits gives 132 bits.
		 * 132 bits, divided by 11, gives 12 words.
		 */
		final int WORD_MASK = 2048 - 1;

		UUID uuid = UUID.randomUUID();

		System.out.println("UUID: " + uuid.toString());

		byte[] uuidMSB = Longs.toByteArray(uuid.getMostSignificantBits());
		byte[] uuidLSB = Longs.toByteArray(uuid.getLeastSignificantBits());
		byte[] message = Bytes.concat(uuidMSB, uuidLSB);

		// Use SHA256 to generate more bits
		byte[] hash = Crypto.digest(message);

		// Append last 4 bits from hash to end. (Actually 8 bits but we only use 4).
		message = Bytes.concat(message, new byte[] {
			hash[hash.length - 1]
		});

		BigInteger wordBits = new BigInteger(message);

		String[] phraseWords = new String[12];
		for (int i = phraseWords.length; i >= 0; --i) {
			int wordListIndex = wordBits.intValue() & WORD_MASK;
			wordBits = wordBits.shiftRight(11);
			// phraseWords[i] = wordList.get(wordListIndex);
		}

		return String.join(" ", phraseWords);
	}

	@POST
	@Path("/privateKey")
	@Operation(
		summary = "Calculate private key from 12-word BIP39 seed phrase",
		description = "Returns the base58-encoded private key, or \"false\" if phrase is invalid.",
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
	public String getPublicKey(String seedPhrase) {
		// TODO: convert BIP39 seed phrase to private key
		return seedPhrase;
	}

}
