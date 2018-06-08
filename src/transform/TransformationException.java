package transform;

@SuppressWarnings("serial")
public class TransformationException extends Exception {

	public TransformationException(String message) {
		super(message);
	}

	public TransformationException(Exception e) {
		super(e);
	}

}
