package de.tnttastisch.polydb.testrepositories;

import de.tnttastisch.polydb.core.annotations.Param;
import de.tnttastisch.polydb.core.annotations.Query;
import de.tnttastisch.polydb.query.CrudRepository;
import de.tnttastisch.polydb.testentities.Widget;
import de.tnttastisch.polydb.testprojections.WidgetSummary;
import de.tnttastisch.polydb.testprojections.WidgetView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Test repository returning projections rather than entities: interface and record projections from
 * both derived query methods (mapped from the loaded entity) and {@code @Query} (mapped from the
 * selected columns).
 */
public interface WidgetProjectionRepository extends CrudRepository<Widget, UUID> {

    List<WidgetView> findByActiveTrue();

    List<WidgetSummary> findByStatus(Widget.Status status);

    @Query("SELECT name, quantity FROM widgets WHERE quantity > :min ORDER BY quantity")
    List<WidgetSummary> summariesHeavierThan(@Param("min") int min);

    @Query("SELECT * FROM widgets WHERE name = ?1")
    Optional<WidgetView> viewByName(String name);
}
