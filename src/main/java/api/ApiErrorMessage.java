package api;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class ApiErrorMessage {

	protected int error;

	protected String message;

	protected ApiErrorMessage() {
	}

	public ApiErrorMessage(int errorCode, String message) {
		this.error = errorCode;
		this.message = message;
	}

}
