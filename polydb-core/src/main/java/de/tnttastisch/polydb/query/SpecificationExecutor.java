package de.tnttastisch.polydb.query;

import java.util.List;
import java.util.Optional;

/**
 * Runs {@link Specification} filters, PolyDB's equivalent of Spring Data's
 * {@code JpaSpecificationExecutor}. A user repository can extend this alongside
 * {@link CrudRepository}/{@link PagingAndSortingRepository} to gain dynamic, composable querying.
 *
 * <p>A {@code null} specification (or one that produces no condition) matches everything.</p>
 *
 * @param <T> the entity type
 */
public interface SpecificationExecutor<T> {

    /** The single entity matching {@code spec}, or empty; when several match, the first is returned. */
    Optional<T> findOne(Specification<T> spec);

    /** All entities matching {@code spec}. */
    List<T> findAll(Specification<T> spec);

    /** All entities matching {@code spec}, ordered by {@code sort}. */
    List<T> findAll(Specification<T> spec, Sort sort);

    /** A page of entities matching {@code spec}. */
    Page<T> findAll(Specification<T> spec, Pageable pageable);

    /** The number of entities matching {@code spec}. */
    long count(Specification<T> spec);

    /** Whether any entity matches {@code spec}. */
    boolean exists(Specification<T> spec);
}
