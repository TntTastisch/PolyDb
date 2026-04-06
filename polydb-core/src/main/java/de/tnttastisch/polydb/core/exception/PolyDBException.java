package de.tnttastisch.polydb.core.exception;

public class PolyDBException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PolyDBException(String message) {
        super(message);
    }

    public PolyDBException(String message, Throwable cause) {
        super(message, cause);
    }
}
