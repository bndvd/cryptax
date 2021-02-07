package bdn.cryptax.model;

public class TransactionException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public enum TransactionExceptionType {
		INVALID_DATA, EMPTY_DATA
	}
	
	
	private TransactionExceptionType _type;
	

	public TransactionException(TransactionExceptionType type, String message) {
		super(message);
		_type = type;
	}


	public TransactionExceptionType getType() {
		return _type;
	}


	public void setType(TransactionExceptionType type) {
		_type = type;
	}

	
}
