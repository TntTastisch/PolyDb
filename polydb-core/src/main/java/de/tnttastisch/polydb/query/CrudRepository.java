package de.tnttastisch.polydb.query;

import java.util.List;
import java.util.Optional;

/**
 * Standard create/read/update/delete operations for an entity type {@code T} with identifier type
 * {@code ID}. This is the usual base a user repository extends; PolyDB supplies the implementation
 * (see {@link de.tnttastisch.polydb.query.support.RepositoryFactory}), so a declared interface such
 * as {@code interface UserRepository extends CrudRepository<User, UUID> { ... }} is usable without
 * any implementation class.
 *
 * <p>Unlike Spring Data's {@code CrudRepository}, the multi-row reads return {@link List} rather than
 * {@code Iterable} for ergonomics, while the bulk inputs accept any {@link Iterable}. {@code save}
 * performs an upsert (insert when the id is absent or unknown, update otherwise) and also cascades to
 * associated entities as configured on the relations.</p>
 *
 * @param <T>  the entity type this repository manages
 * @param <ID> the type of the entity's {@code @Id} field
 */
public interface CrudRepository<T, ID> extends Repository<T, ID> {

    /**
     * Persists the given entity, inserting a new row or updating the existing one depending on
     * whether a row with its id already exists. Cascades to associated entities as configured.
     *
     * @param entity the entity to persist
     * @param <S>    the concrete entity subtype, preserved in the return value
     * @return the saved entity
     */
    <S extends T> S save(S entity);

    /**
     * Persists every entity in {@code entities}, in iteration order, each via {@link #save(Object)}.
     *
     * @param entities the entities to persist
     * @param <S>      the concrete entity subtype
     * @return the saved entities, in the same order
     */
    <S extends T> List<S> saveAll(Iterable<S> entities);

    /**
     * Looks up a single entity by its primary key.
     *
     * @param id the primary-key value to match
     * @return the matching entity, or {@link Optional#empty()} if none matches
     */
    Optional<T> findById(ID id);

    /**
     * Returns whether an entity with the given primary key exists.
     *
     * @param id the primary-key value to test
     * @return {@code true} if a row with that id exists
     */
    boolean existsById(ID id);

    /**
     * Returns every row of the entity's table.
     *
     * @return all entities; an empty list when the table has no rows
     */
    List<T> findAll();

    /**
     * Returns the entities whose ids are in {@code ids}. Missing ids are simply absent from the
     * result, which therefore may be smaller than the input and is not ordered to match it.
     *
     * @param ids the primary-key values to load
     * @return the matching entities
     */
    List<T> findAllById(Iterable<ID> ids);

    /**
     * Counts all rows of the entity's table.
     *
     * @return the total number of rows
     */
    long count();

    /**
     * Deletes the row identified by the entity's primary key. Only the id is consulted.
     *
     * @param entity the entity whose row should be removed
     */
    void delete(T entity);

    /**
     * Deletes the row with the given primary key. A {@code null} id is a no-op.
     *
     * @param id the primary-key value of the row to remove
     */
    void deleteById(ID id);

    /**
     * Deletes the rows identified by the given primary keys.
     *
     * @param ids the primary-key values of the rows to remove
     */
    void deleteAllById(Iterable<? extends ID> ids);

    /**
     * Deletes the rows identified by the given entities' primary keys.
     *
     * @param entities the entities whose rows should be removed
     */
    void deleteAll(Iterable<? extends T> entities);

    /**
     * Deletes every row of the entity's table.
     */
    void deleteAll();
}
