package de.tnttastisch.polydb.core.exception;

/**
 * Raised when an optimistic-locking update finds no matching row for the expected {@code @Version},
 * which means another transaction modified (or removed) the entity in the meantime. Callers typically
 * reload the entity and retry.
 */
public class OptimisticLockException extends PolyDBException {

    private static final long serialVersionUID = 1L;

    public OptimisticLockException(String message) {
        super(message);
    }
}
