package org.qora.transform;

public class TransformationException extends Exception {

	private static final long serialVersionUID = 1132363278761469714L;

	public TransformationException() {
	}

	public TransformationException(String message) {
		super(message);
	}

	public TransformationException(String message, Throwable cause) {
		super(message, cause);
	}

	public TransformationException(Throwable cause) {
		super(cause);
	}

}
