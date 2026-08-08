package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.List;

/**
 * A single, dialect-independent unit of schema or data change — the shared currency of PolyDB's
 * migration engine. Both automatic migration (via the schema comparator) and manual migrations (via
 * the fluent {@code MigrationBuilder}) produce nothing but {@code MigrationOperation}s, which the
 * {@code MigrationExecutor} renders to SQL through the {@link Dialect} and applies.
 *
 * <p>Modelling changes as data rather than raw SQL is what makes ordering, transactional rollback,
 * SQL preview, dry-run and automatic reverse possible: an operation knows how to render itself
 * ({@link #toStatements(Dialect)}), how to describe itself ({@link #describe()}) and, when it has a
 * symmetric counterpart, how to {@linkplain #reverse() reverse} itself.</p>
 */
public interface MigrationOperation {

    /**
     * Renders this operation to one or more executable statements for the given dialect. Returns an
     * empty list when the dialect cannot express the operation (e.g. a NoSQL dialect with no foreign
     * keys), so the executor simply skips it.
     */
    List<SqlStatement> toStatements(Dialect dialect);

    /** Short human-readable summary used in migration plans and logs, e.g. {@code "Create table users"}. */
    String describe();

    /** Whether {@link #reverse()} can produce an inverse operation for automatic rollback. */
    default boolean isReversible() {
        return false;
    }

    /**
     * The inverse operation, used for automatic rollback of a migration.
     *
     * @throws IrreversibleOperationException if this operation has no automatic inverse
     */
    default MigrationOperation reverse() {
        throw new IrreversibleOperationException(describe());
    }
}
