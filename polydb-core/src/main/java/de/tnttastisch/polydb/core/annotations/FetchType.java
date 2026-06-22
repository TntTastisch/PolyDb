package de.tnttastisch.polydb.core.annotations;

/**
 * Defines when an associated entity (or collection) is loaded from the database.
 *
 * <ul>
 *     <li>{@link #EAGER} &ndash; the relation is resolved as part of the owning query.</li>
 *     <li>{@link #LAZY} &ndash; the relation is not resolved automatically; it is left for an
 *     explicit, deferred load. PolyDB currently does not generate runtime proxies, so lazy
 *     relations are simply not populated automatically (see the README for details).</li>
 * </ul>
 */
public enum FetchType {
    LAZY,
    EAGER
}
