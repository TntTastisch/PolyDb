package de.tnttastisch.polydb.testrepositories;

import de.tnttastisch.polydb.query.CrudRepository;

/**
 * Generic intermediate repository interface used to verify that entity/id type resolution works
 * through more than one level of interface inheritance (see {@link DeepWidgetRepository}).
 *
 * @param <T>  the entity type
 * @param <ID> the id type
 */
public interface BaseRepository<T, ID> extends CrudRepository<T, ID> {
}
