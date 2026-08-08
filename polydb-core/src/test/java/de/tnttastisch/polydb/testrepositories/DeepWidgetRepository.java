package de.tnttastisch.polydb.testrepositories;

import de.tnttastisch.polydb.testentities.Widget;

import java.util.UUID;

/**
 * Two-levels-deep repository interface ({@code DeepWidgetRepository -> BaseRepository -> CrudRepository
 * -> Repository}) used to assert the metadata resolver follows the generic hierarchy to find the
 * concrete {@code (Widget, UUID)} type arguments.
 */
public interface DeepWidgetRepository extends BaseRepository<Widget, UUID> {
}
