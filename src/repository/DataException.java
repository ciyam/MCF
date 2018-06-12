package repository;

public class DataException extends Exception {

	private static final long serialVersionUID = -3963965667288257605L;

	public DataException() {
	}

	public DataException(String message) {
		super(message);
	}

	public DataException(String message, Throwable cause) {
		super(message, cause);
	}

	public DataException(Throwable cause) {
		super(cause);
	}

}
