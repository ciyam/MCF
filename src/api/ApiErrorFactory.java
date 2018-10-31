package api;

import globalization.Translator;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ApiErrorFactory {

	private class ErrorMessageEntry {

		String templateKey;
		String defaultTemplate;
		AbstractMap.Entry<String, Object>[] templateValues;

		public ErrorMessageEntry(String templateKey, String defaultTemplate, AbstractMap.Entry<String, Object>... templateValues) {
			this.templateKey = templateKey;
			this.defaultTemplate = defaultTemplate;
			this.templateValues = templateValues;
		}
	}

	private Translator translator;
	private Map<ApiError, ErrorMessageEntry> errorMessages;

	public ApiErrorFactory(Translator translator) {
		this.translator = translator;

		this.errorMessages = new HashMap<ApiError, ErrorMessageEntry>();

		//COMMON
		this.errorMessages.put(ApiError.UNKNOWN, createErrorMessageEntry(ApiError.UNKNOWN, "unknown error"));
		this.errorMessages.put(ApiError.JSON, createErrorMessageEntry(ApiError.JSON, "failed to parse json message"));
		this.errorMessages.put(ApiError.NO_BALANCE, createErrorMessageEntry(ApiError.NO_BALANCE, "not enough balance"));
		this.errorMessages.put(ApiError.NOT_YET_RELEASED, createErrorMessageEntry(ApiError.NOT_YET_RELEASED, "that feature is not yet released"));

		//VALIDATION		
		this.errorMessages.put(ApiError.INVALID_SIGNATURE, createErrorMessageEntry(ApiError.INVALID_SIGNATURE, "invalid signature"));
		this.errorMessages.put(ApiError.INVALID_ADDRESS, createErrorMessageEntry(ApiError.INVALID_ADDRESS, "invalid address"));
		this.errorMessages.put(ApiError.INVALID_SEED, createErrorMessageEntry(ApiError.INVALID_SEED, "invalid seed"));
		this.errorMessages.put(ApiError.INVALID_AMOUNT, createErrorMessageEntry(ApiError.INVALID_AMOUNT, "invalid amount"));
		this.errorMessages.put(ApiError.INVALID_FEE, createErrorMessageEntry(ApiError.INVALID_FEE, "invalid fee"));
		this.errorMessages.put(ApiError.INVALID_SENDER, createErrorMessageEntry(ApiError.INVALID_SENDER, "invalid sender"));
		this.errorMessages.put(ApiError.INVALID_RECIPIENT, createErrorMessageEntry(ApiError.INVALID_RECIPIENT, "invalid recipient"));
		this.errorMessages.put(ApiError.INVALID_NAME_LENGTH, createErrorMessageEntry(ApiError.INVALID_NAME_LENGTH, "invalid name length"));
		this.errorMessages.put(ApiError.INVALID_VALUE_LENGTH, createErrorMessageEntry(ApiError.INVALID_VALUE_LENGTH, "invalid value length"));
		this.errorMessages.put(ApiError.INVALID_NAME_OWNER, createErrorMessageEntry(ApiError.INVALID_NAME_OWNER, "invalid name owner"));
		this.errorMessages.put(ApiError.INVALID_BUYER, createErrorMessageEntry(ApiError.INVALID_BUYER, "invalid buyer"));
		this.errorMessages.put(ApiError.INVALID_PUBLIC_KEY, createErrorMessageEntry(ApiError.INVALID_PUBLIC_KEY, "invalid public key"));
		this.errorMessages.put(ApiError.INVALID_OPTIONS_LENGTH, createErrorMessageEntry(ApiError.INVALID_OPTIONS_LENGTH, "invalid options length"));
		this.errorMessages.put(ApiError.INVALID_OPTION_LENGTH, createErrorMessageEntry(ApiError.INVALID_OPTION_LENGTH, "invalid option length"));
		this.errorMessages.put(ApiError.INVALID_DATA, createErrorMessageEntry(ApiError.INVALID_DATA, "invalid data"));
		this.errorMessages.put(ApiError.INVALID_DATA_LENGTH, createErrorMessageEntry(ApiError.INVALID_DATA_LENGTH, "invalid data length"));
		this.errorMessages.put(ApiError.INVALID_UPDATE_VALUE, createErrorMessageEntry(ApiError.INVALID_UPDATE_VALUE, "invalid update value"));
		this.errorMessages.put(ApiError.KEY_ALREADY_EXISTS, createErrorMessageEntry(ApiError.KEY_ALREADY_EXISTS, "key already exists, edit is false"));
		this.errorMessages.put(ApiError.KEY_NOT_EXISTS, createErrorMessageEntry(ApiError.KEY_NOT_EXISTS, "the key does not exist"));
// TODO
//		this.errorMessages.put(ApiError.LAST_KEY_IS_DEFAULT_KEY_ERROR, createErrorMessageEntry(ApiError.LAST_KEY_IS_DEFAULT_KEY_ERROR, 
//                    "you can't delete the key \"${key}\" if it is the only key",
//                    new AbstractMap.SimpleEntry<String.Object>("key", Qorakeys.DEFAULT.toString())));
		this.errorMessages.put(ApiError.FEE_LESS_REQUIRED, createErrorMessageEntry(ApiError.FEE_LESS_REQUIRED, "fee less required"));
		this.errorMessages.put(ApiError.WALLET_NOT_IN_SYNC, createErrorMessageEntry(ApiError.WALLET_NOT_IN_SYNC, "wallet needs to be synchronized"));
		this.errorMessages.put(ApiError.INVALID_NETWORK_ADDRESS, createErrorMessageEntry(ApiError.INVALID_NETWORK_ADDRESS, "invalid network address"));

		//WALLET
		this.errorMessages.put(ApiError.WALLET_NO_EXISTS, createErrorMessageEntry(ApiError.WALLET_NO_EXISTS, "wallet does not exist"));
		this.errorMessages.put(ApiError.WALLET_ADDRESS_NO_EXISTS, createErrorMessageEntry(ApiError.WALLET_ADDRESS_NO_EXISTS, "address does not exist in wallet"));
		this.errorMessages.put(ApiError.WALLET_LOCKED, createErrorMessageEntry(ApiError.WALLET_LOCKED, "wallet is locked"));
		this.errorMessages.put(ApiError.WALLET_ALREADY_EXISTS, createErrorMessageEntry(ApiError.WALLET_ALREADY_EXISTS, "wallet already exists"));
		this.errorMessages.put(ApiError.WALLET_API_CALL_FORBIDDEN_BY_USER, createErrorMessageEntry(ApiError.WALLET_API_CALL_FORBIDDEN_BY_USER, "user denied api call"));

		//BLOCK
		this.errorMessages.put(ApiError.BLOCK_NO_EXISTS, createErrorMessageEntry(ApiError.BLOCK_NO_EXISTS, "block does not exist"));

		//TRANSACTIONS
		this.errorMessages.put(ApiError.TRANSACTION_NO_EXISTS, createErrorMessageEntry(ApiError.TRANSACTION_NO_EXISTS, "transactions does not exist"));
		this.errorMessages.put(ApiError.PUBLIC_KEY_NOT_FOUND, createErrorMessageEntry(ApiError.PUBLIC_KEY_NOT_FOUND, "public key not found"));

		//NAMING
		this.errorMessages.put(ApiError.NAME_NO_EXISTS, createErrorMessageEntry(ApiError.NAME_NO_EXISTS, "name does not exist"));
		this.errorMessages.put(ApiError.NAME_ALREADY_EXISTS, createErrorMessageEntry(ApiError.NAME_ALREADY_EXISTS, "name already exists"));
		this.errorMessages.put(ApiError.NAME_ALREADY_FOR_SALE, createErrorMessageEntry(ApiError.NAME_ALREADY_FOR_SALE, "name already for sale"));
		this.errorMessages.put(ApiError.NAME_NOT_LOWER_CASE, createErrorMessageEntry(ApiError.NAME_NOT_LOWER_CASE, "name must be lower case"));
		this.errorMessages.put(ApiError.NAME_SALE_NO_EXISTS, createErrorMessageEntry(ApiError.NAME_SALE_NO_EXISTS, "namesale does not exist"));
		this.errorMessages.put(ApiError.BUYER_ALREADY_OWNER, createErrorMessageEntry(ApiError.BUYER_ALREADY_OWNER, "buyer is already owner"));

		//POLLS
		this.errorMessages.put(ApiError.POLL_NO_EXISTS, createErrorMessageEntry(ApiError.POLL_NO_EXISTS, "poll does not exist"));
		this.errorMessages.put(ApiError.POLL_ALREADY_EXISTS, createErrorMessageEntry(ApiError.POLL_ALREADY_EXISTS, "poll already exists"));
		this.errorMessages.put(ApiError.DUPLICATE_OPTION, createErrorMessageEntry(ApiError.DUPLICATE_OPTION, "not all options are unique"));
		this.errorMessages.put(ApiError.POLL_OPTION_NO_EXISTS, createErrorMessageEntry(ApiError.POLL_OPTION_NO_EXISTS, "option does not exist"));
		this.errorMessages.put(ApiError.ALREADY_VOTED_FOR_THAT_OPTION, createErrorMessageEntry(ApiError.ALREADY_VOTED_FOR_THAT_OPTION, "already voted for that option"));

		//ASSETS
		this.errorMessages.put(ApiError.INVALID_ASSET_ID, createErrorMessageEntry(ApiError.INVALID_ASSET_ID, "invalid asset id"));

		//NAME PAYMENTS
// TODO
//		this.errorMessages.put(ApiError.NAME_NOT_REGISTERED, createErrorMessageEntry(ApiError.NAME_NOT_REGISTERED, NameResult.NAME_NOT_REGISTERED.getStatusMessage()));
//		this.errorMessages.put(ApiError.NAME_FOR_SALE, createErrorMessageEntry(ApiError.NAME_FOR_SALE, NameResult.NAME_FOR_SALE.getStatusMessage()));
//		this.errorMessages.put(ApiError.NAME_WITH_SPACE, createErrorMessageEntry(ApiError.NAME_WITH_SPACE, NameResult.NAME_WITH_SPACE.getStatusMessage()));
		//AT
// TODO
//		this.errorMessages.put(ApiError.INVALID_DESC_LENGTH, createErrorMessageEntry(ApiError.INVALID_DESC_LENGTH, 
//                    "invalid description length. max length ${MAX_LENGTH}", 
//                    new AbstractMap.SimpleEntry<String, Object>("MAX_LENGTH", AT_Constants.DESC_MAX_LENGTH));
		this.errorMessages.put(ApiError.EMPTY_CODE, createErrorMessageEntry(ApiError.EMPTY_CODE, "code is empty"));
		this.errorMessages.put(ApiError.DATA_SIZE, createErrorMessageEntry(ApiError.DATA_SIZE, "invalid data length"));
		this.errorMessages.put(ApiError.NULL_PAGES, createErrorMessageEntry(ApiError.NULL_PAGES, "invalid pages"));
		this.errorMessages.put(ApiError.INVALID_TYPE_LENGTH, createErrorMessageEntry(ApiError.INVALID_TYPE_LENGTH, "invalid type length"));
		this.errorMessages.put(ApiError.INVALID_TAGS_LENGTH, createErrorMessageEntry(ApiError.INVALID_TAGS_LENGTH, "invalid tags length"));
		this.errorMessages.put(ApiError.INVALID_CREATION_BYTES, createErrorMessageEntry(ApiError.INVALID_CREATION_BYTES, "error in creation bytes"));

		//BLOG
		this.errorMessages.put(ApiError.BODY_EMPTY, createErrorMessageEntry(ApiError.BODY_EMPTY, "invalid body it must not be empty"));
		this.errorMessages.put(ApiError.BLOG_DISABLED, createErrorMessageEntry(ApiError.BLOG_DISABLED, "this blog is disabled"));
		this.errorMessages.put(ApiError.NAME_NOT_OWNER, createErrorMessageEntry(ApiError.NAME_NOT_OWNER, "the creator address does not own the author name"));
//		this.errorMessages.put(ApiError.TX_AMOUNT, createErrorMessageEntry(ApiError.TX_AMOUNT,
//                    "the data size is too large - currently only ${BATCH_TX_AMOUNT} arbitrary transactions are allowed at once!",
//                    new AbstractMap.SimpleEntry<String,Object>("BATCH_TX_AMOUNT", BATCH_TX_AMOUNT)));
		this.errorMessages.put(ApiError.BLOG_ENTRY_NO_EXISTS, createErrorMessageEntry(ApiError.BLOG_ENTRY_NO_EXISTS, "transaction with this signature contains no entries!"));
		this.errorMessages.put(ApiError.BLOG_EMPTY, createErrorMessageEntry(ApiError.BLOG_EMPTY, "this blog is empty"));
		this.errorMessages.put(ApiError.POSTID_EMPTY, createErrorMessageEntry(ApiError.POSTID_EMPTY, "the attribute postid is empty! this is the signature of the post you want to comment"));
		this.errorMessages.put(ApiError.POST_NOT_EXISTING, createErrorMessageEntry(ApiError.POST_NOT_EXISTING, "for the given postid no blogpost to comment was found"));
		this.errorMessages.put(ApiError.COMMENTING_DISABLED, createErrorMessageEntry(ApiError.COMMENTING_DISABLED, "commenting is for this blog disabled"));
		this.errorMessages.put(ApiError.COMMENT_NOT_EXISTING, createErrorMessageEntry(ApiError.COMMENT_NOT_EXISTING, "for the given signature no comment was found"));
		this.errorMessages.put(ApiError.INVALID_COMMENT_OWNER, createErrorMessageEntry(ApiError.INVALID_COMMENT_OWNER, "invalid comment owner"));

		//MESSAGES
		this.errorMessages.put(ApiError.MESSAGE_FORMAT_NOT_HEX, createErrorMessageEntry(ApiError.MESSAGE_FORMAT_NOT_HEX, "the Message format is not hex - correct the text or use isTextMessage = true"));
		this.errorMessages.put(ApiError.MESSAGE_BLANK, createErrorMessageEntry(ApiError.MESSAGE_BLANK, "The message attribute is missing or content is blank"));
		this.errorMessages.put(ApiError.NO_PUBLIC_KEY, createErrorMessageEntry(ApiError.NO_PUBLIC_KEY, "The recipient has not yet performed any action in the blockchain.\nYou can't send an encrypted message to him."));
		this.errorMessages.put(ApiError.MESSAGESIZE_EXCEEDED, createErrorMessageEntry(ApiError.MESSAGESIZE_EXCEEDED, "Message size exceeded!"));

	}

	//XXX: replace singleton pattern by dependency injection?
	private static ApiErrorFactory instance;

	public static ApiErrorFactory getInstance() {
		if (instance == null) {
			instance = new ApiErrorFactory(Translator.getInstance());
		}

		return instance;
	}
	
	private ErrorMessageEntry createErrorMessageEntry(ApiError errorCode, String defaultTemplate, AbstractMap.SimpleEntry<String, Object>... templateValues) {
		String templateKey = String.format(Constants.APIERROR_KEY, errorCode.name());
		return new ErrorMessageEntry(templateKey, defaultTemplate, templateValues);
	}

	public String getErrorMessage(ApiError error) {
		return getErrorMessage(null, error);
	}
	
	public String getErrorMessage(Locale locale, ApiError error) {
		ErrorMessageEntry errorMessage = this.errorMessages.get(error);
		String message = this.translator.translate(locale, Constants.APIERROR_CONTEXT_PATH, errorMessage.templateKey, errorMessage.defaultTemplate, errorMessage.templateValues);
		return message;
	}

	public ApiException createError(ApiError error) {
		return createError(error, null);
	}
	
	public ApiException createError(Locale locale, ApiError error) {
		return createError(locale, error, null);
	}
	
	public ApiException createError(ApiError error, Throwable throwable) {
		return createError(null, error, throwable);
	}
	
	public ApiException createError(Locale locale, ApiError error, Throwable throwable) {
		// TODO: handle AT errors
// old AT error handling
//		JSONObject jsonObject = new JSONObject();
//		jsonObject.put("error", error);
//		if ( error > Transaction.AT_ERROR )
//		{
//			jsonObject.put("message", AT_Error.getATError(error - Transaction.AT_ERROR) );
//		}
//		else
//		{
//			jsonObject.put("message", this.errorMessages.get(error));
//		}
//		
//		
//		return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(jsonObject.toJSONString()).build());

		String message = getErrorMessage(locale, error);
		return new ApiException(error.getStatus(), error.getCode(), message, throwable);
	}
}
