package de.tnttastisch.polydb.core.exception;

/**
 * Unchecked exception used throughout PolyDB to signal failures that callers are not generally
 * expected to recover from: mapping/configuration errors (e.g. an entity without an {@code @Id}),
 * reflection failures while reading or writing fields, and wrapped {@link java.sql.SQLException}s
 * from schema, history and query operations. Being a {@link RuntimeException}, it keeps the public
 * API free of checked-exception clutter.
 */
public class PolyDBException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception with a descriptive message and no underlying cause. */
    public PolyDBException(String message) {
        super(message);
    }

    /** Creates an exception that wraps a lower-level cause (typically a {@link java.sql.SQLException}). */
    public PolyDBException(String message, Throwable cause) {
        super(message, cause);
    }
}
