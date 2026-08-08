package de.tnttastisch.polydb.query;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A small static registry of {@link EntityListener}s that repositories notify after a save or delete.
 * Thread-safe and dependency-free: register a listener once at startup and every repository fires it.
 * Listener exceptions propagate to the caller (a listener is part of the operation), so keep them
 * lightweight or guard them yourself.
 */
public final class EntityEvents {

    private static final List<EntityListener> LISTENERS = new CopyOnWriteArrayList<>();

    private EntityEvents() {
    }

    /** Registers a listener. */
    public static void addListener(EntityListener listener) {
        LISTENERS.add(listener);
    }

    /** Unregisters a listener. */
    public static void removeListener(EntityListener listener) {
        LISTENERS.remove(listener);
    }

    /** Removes all listeners. */
    public static void clear() {
        LISTENERS.clear();
    }

    static void fireAfterSave(Object entity) {
        for (EntityListener listener : LISTENERS) {
            listener.afterSave(entity);
        }
    }

    static void fireAfterDelete(Object entity) {
        for (EntityListener listener : LISTENERS) {
            listener.afterDelete(entity);
        }
    }
}
