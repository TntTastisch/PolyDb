package de.tnttastisch.polydb.testrepositories;

import de.tnttastisch.polydb.query.Page;
import de.tnttastisch.polydb.query.Pageable;
import de.tnttastisch.polydb.query.PagingAndSortingRepository;
import de.tnttastisch.polydb.query.Slice;
import de.tnttastisch.polydb.query.Sort;
import de.tnttastisch.polydb.testentities.Widget;

import java.util.List;
import java.util.UUID;

/**
 * Test repository exercising sorting and pagination: the inherited
 * {@link PagingAndSortingRepository#findAll(Sort)} / {@link PagingAndSortingRepository#findAll(Pageable)}
 * plus derived finders that take a trailing {@link Sort} or {@link Pageable} and return a
 * {@link Page}, a {@link Slice}, or a plain {@link List}.
 */
public interface WidgetPagingRepository extends PagingAndSortingRepository<Widget, UUID> {

    Page<Widget> findByActiveTrue(Pageable pageable);

    Slice<Widget> findByStatus(Widget.Status status, Pageable pageable);

    List<Widget> findByActiveTrue(Sort sort);

    List<Widget> findByQuantityGreaterThan(int quantity, Pageable pageable);
}
