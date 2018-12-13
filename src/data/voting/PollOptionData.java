package data.voting;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

//All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class PollOptionData {

	// Properties
	private String optionName;

	// Constructors

	// For JAX-RS
	protected PollOptionData() {
	}

	public PollOptionData(String optionName) {
		this.optionName = optionName;
	}

	// Getters/setters

	public String getOptionName() {
		return this.optionName;
	}

}
