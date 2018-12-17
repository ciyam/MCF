package api;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class ApiErrorMessage {

	public int error;

	public String message;

	ApiErrorMessage() {
	}

	ApiErrorMessage(int errorCode, String message) {
		this.error = errorCode;
		this.message = message;
	}
}
