package api;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ApiErrorMessage {

	@XmlElement(name = "error")
	public int error;

	@XmlElement(name = "message")
	public String message;

	ApiErrorMessage() {
	}

	ApiErrorMessage(int errorCode, String message) {
		this.error = errorCode;
		this.message = message;
	}
}
