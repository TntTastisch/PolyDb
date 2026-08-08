package de.tnttastisch.polydb.testrepositories;

import de.tnttastisch.polydb.query.CrudRepository;
import de.tnttastisch.polydb.testentities.Widget;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Test repository exercising the derived-query engine across the full operator surface: equality and
 * case-insensitive equality, relational and range comparisons, {@code Between}, {@code In}, string
 * matching, {@code null}/boolean checks, {@code And}/{@code Or} composition, ordering, {@code Top}
 * limiting, and the {@code count}/{@code exists}/{@code delete} actions.
 */
public interface WidgetQueryRepository extends CrudRepository<Widget, UUID> {

    List<Widget> findByName(String name);

    Optional<Widget> findByNameIgnoreCase(String name);

    List<Widget> findByQuantityGreaterThan(int quantity);

    List<Widget> findByQuantityGreaterThanEqual(int quantity);

    List<Widget> findByQuantityBetween(int low, int high);

    List<Widget> findByActiveTrue();

    List<Widget> findByActiveFalse();

    List<Widget> findByStatus(Widget.Status status);

    List<Widget> findByStatusIn(Collection<Widget.Status> statuses);

    List<Widget> findByNameContaining(String part);

    List<Widget> findByNameStartingWith(String prefix);

    List<Widget> findByNameEndingWith(String suffix);

    List<Widget> findByPriceIsNull();

    List<Widget> findByPriceIsNotNull();

    List<Widget> findByNameAndQuantityGreaterThan(String name, int quantity);

    List<Widget> findByStatusOrQuantityGreaterThan(Widget.Status status, int quantity);

    List<Widget> findByActiveTrueOrderByQuantityDesc();

    List<Widget> findByActiveTrueOrderByQuantityAscNameDesc();

    List<Widget> findTop2ByActiveTrueOrderByQuantityDesc();

    Widget findFirstByOrderByQuantityDesc();

    long countByActiveTrue();

    boolean existsByName(String name);

    long deleteByStatus(Widget.Status status);
}
