package de.tnttastisch.polydb.query;

import java.util.List;

/**
 * Adds sorting and pagination to the standard {@link CrudRepository} surface. A user repository can
 * extend this instead of {@code CrudRepository} to gain {@link #findAll(Sort)} and
 * {@link #findAll(Pageable)} for free. Model of Spring Data's {@code PagingAndSortingRepository}.
 *
 * @param <T>  the entity type
 * @param <ID> the id type
 */
public interface PagingAndSortingRepository<T, ID> extends CrudRepository<T, ID> {

    /**
     * Returns all entities sorted by the given ordering.
     *
     * @param sort the ordering to apply ({@link Sort#unsorted()} for none)
     * @return the sorted entities
     */
    List<T> findAll(Sort sort);

    /**
     * Returns a page of entities.
     *
     * @param pageable the paging (and sorting) request
     * @return the requested page, including total counts
     */
    Page<T> findAll(Pageable pageable);
}
