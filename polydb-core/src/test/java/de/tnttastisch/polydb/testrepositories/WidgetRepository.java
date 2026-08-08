package de.tnttastisch.polydb.testrepositories;

import de.tnttastisch.polydb.query.CrudRepository;
import de.tnttastisch.polydb.testentities.Widget;

import java.util.List;
import java.util.UUID;

/**
 * Test repository interface used to exercise the dynamic-proxy path: standard CRUD is inherited from
 * {@link CrudRepository}, {@link #isEmpty()} is a {@code default} method that composes a CRUD call,
 * and {@link #findByName(String)} is a custom query method (unsupported until derived queries land).
 */
public interface WidgetRepository extends CrudRepository<Widget, UUID> {

    /** Custom query method — rejected by the foundation's query-method executor. */
    List<Widget> findByName(String name);

    /** Default method that delegates to an inherited CRUD method through the proxy. */
    default boolean isEmpty() {
        return count() == 0;
    }
}
