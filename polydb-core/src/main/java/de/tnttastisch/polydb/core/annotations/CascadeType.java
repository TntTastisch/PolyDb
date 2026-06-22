package de.tnttastisch.polydb.core.annotations;

/**
 * Operations that are cascaded from an owning entity to its associated entities.
 *
 * <ul>
 *     <li>{@link #PERSIST} &ndash; associated entities are saved when the owner is saved.</li>
 *     <li>{@link #MERGE} &ndash; associated entities are updated when the owner is updated.</li>
 *     <li>{@link #REMOVE} &ndash; associated entities are deleted when the owner is deleted.</li>
 *     <li>{@link #ALL} &ndash; shorthand for {@link #PERSIST}, {@link #MERGE} and {@link #REMOVE}.</li>
 * </ul>
 */
public enum CascadeType {
    PERSIST,
    MERGE,
    REMOVE,
    ALL
}
