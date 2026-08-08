package de.tnttastisch.polydb.testrepositories;

import de.tnttastisch.polydb.query.PagingAndSortingRepository;
import de.tnttastisch.polydb.query.SpecificationExecutor;
import de.tnttastisch.polydb.testentities.Widget;

import java.util.UUID;

/**
 * Test repository combining CRUD, paging/sorting and {@link SpecificationExecutor} so dynamic
 * {@code Specification} filters can be exercised end-to-end.
 */
public interface WidgetSpecRepository
        extends PagingAndSortingRepository<Widget, UUID>, SpecificationExecutor<Widget> {
}
