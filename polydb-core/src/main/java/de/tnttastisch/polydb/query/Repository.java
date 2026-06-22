package de.tnttastisch.polydb.query;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for a single entity type {@code T}. Implementations expose scalar CRUD and
 * are free to layer additional behaviour on top (for example {@link JdbcRepository} also resolves
 * entity relations and propagates cascades).
 *
 * <p>The {@code id} arguments are typed as {@link Object} because the identifier type is not known
 * at this level; it is whatever type the entity declares for its {@code @Id} field.</p>
 */
public interface Repository<T> {

    /**
     * Persists the entity, choosing insert or update based on whether a row with its id already
     * exists. Implementations may also cascade the operation to associated entities.
     *
     * @param entity the entity to persist
     */
    void save(T entity);

    /**
     * Looks up a single entity by its primary key.
     *
     * @param id the primary-key value to match
     * @return the matching entity, or {@link Optional#empty()} if no row matches
     */
    Optional<T> findById(Object id);

    /**
     * Returns every row of the entity's table.
     *
     * @return all entities; an empty list when the table has no rows
     */
    List<T> findAll();

    /**
     * Deletes the row identified by the entity's primary key. The non-id state of the argument is
     * not consulted; only its id matters.
     *
     * @param entity the entity whose row should be removed
     */
    void delete(T entity);

    /**
     * Deletes the row with the given primary key. A {@code null} id is a no-op.
     *
     * @param id the primary-key value of the row to remove
     */
    void deleteById(Object id);
}
