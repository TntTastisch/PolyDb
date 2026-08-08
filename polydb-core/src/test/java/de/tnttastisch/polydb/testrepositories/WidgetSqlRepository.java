package de.tnttastisch.polydb.testrepositories;

import de.tnttastisch.polydb.core.annotations.Modifying;
import de.tnttastisch.polydb.core.annotations.Param;
import de.tnttastisch.polydb.core.annotations.Query;
import de.tnttastisch.polydb.query.CrudRepository;
import de.tnttastisch.polydb.testentities.Widget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Test repository exercising {@link Query}: named ({@code :min}), positional ({@code ?1}) and
 * sequential ({@code ?}) binding; entity, {@code Optional}, scalar and scalar-list return types; and
 * {@link Modifying} writes.
 */
public interface WidgetSqlRepository extends CrudRepository<Widget, UUID> {

    @Query("SELECT * FROM widgets WHERE quantity > :min ORDER BY quantity")
    List<Widget> heavierThan(@Param("min") int min);

    @Query("SELECT * FROM widgets WHERE name = ?1")
    Optional<Widget> byName(String name);

    @Query("SELECT COUNT(*) FROM widgets WHERE active = true")
    long countActive();

    @Query("SELECT name FROM widgets ORDER BY name")
    List<String> allNames();

    @Query("SELECT * FROM widgets WHERE status = ?")
    List<Widget> byStatus(Widget.Status status);

    @Modifying
    @Query("UPDATE widgets SET active = false WHERE quantity < :threshold")
    int deactivateBelow(@Param("threshold") int threshold);

    @Modifying
    @Query("DELETE FROM widgets WHERE active = false")
    int deleteInactive();
}
