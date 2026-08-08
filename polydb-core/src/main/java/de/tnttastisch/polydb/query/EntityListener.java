package de.tnttastisch.polydb.query;

/**
 * A hook notified after a repository persists or deletes an entity — PolyDB's lightweight take on
 * domain events. Register implementations with {@link EntityEvents}. Both callbacks default to no-ops,
 * so a listener overrides only what it cares about.
 */
public interface EntityListener {

    /** Called after an entity is inserted or updated. */
    default void afterSave(Object entity) {
    }

    /** Called after an entity is deleted (or soft-deleted). */
    default void afterDelete(Object entity) {
    }
}
