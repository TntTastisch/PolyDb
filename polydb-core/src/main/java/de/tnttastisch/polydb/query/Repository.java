package de.tnttastisch.polydb.query;

/**
 * Root marker for the repository hierarchy, carrying the entity type {@code T} and its identifier
 * type {@code ID}. It declares no methods on purpose: it exists so a user can declare their own
 * repository interface and have PolyDB synthesise an implementation for it at runtime (via a dynamic
 * proxy), the same way Spring Data does.
 *
 * <p>Most repositories extend {@link CrudRepository} to inherit the standard create/read/update/
 * delete operations; a bare {@code Repository} is only useful when you want to expose a hand-picked
 * subset of operations. The two type parameters let PolyDB resolve, by reflection, which entity a
 * repository serves and what type its {@code @Id} is, so query methods can be type-checked against
 * the right entity.</p>
 *
 * @param <T>  the entity type this repository manages
 * @param <ID> the type of the entity's {@code @Id} field
 */
public interface Repository<T, ID> {
}
