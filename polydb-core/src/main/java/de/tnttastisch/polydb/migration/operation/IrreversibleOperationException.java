package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.core.exception.PolyDBException;

/**
 * Thrown when an automatic rollback is requested for a {@link MigrationOperation} that has no inverse
 * (e.g. dropping a table or column whose prior definition was not captured, or a data update with no
 * recorded old values). Complex migrations should provide an explicit {@code down()} instead of
 * relying on {@link MigrationOperation#reverse()} for such operations.
 */
public class IrreversibleOperationException extends PolyDBException {

    private static final long serialVersionUID = 1L;

    public IrreversibleOperationException(String operationDescription) {
        super("Operation cannot be reversed automatically: " + operationDescription
                + " — define an explicit down() migration instead");
    }
}
