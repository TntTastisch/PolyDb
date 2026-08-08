package de.tnttastisch.polydb.examples.repository;

import de.tnttastisch.polydb.examples.entity.User;
import de.tnttastisch.polydb.query.CrudRepository;

import java.util.UUID;

/**
 * Example of a user-declared repository interface. By extending {@link CrudRepository} with the entity
 * type ({@link User}) and its id type ({@link UUID}), it inherits the full standard CRUD surface with
 * no implementation class: PolyDB synthesises the implementation at runtime (see
 * {@code polyDB.getRepository(UserRepository.class)}).
 *
 * <p>This is the type-safe counterpart to the quick {@code polyDB.repository(User.class)} path — the
 * id type is {@code UUID} here rather than {@code Object}. It is also the natural place to add custom
 * query methods (e.g. {@code findByUsername}) as those capabilities land.</p>
 */
public interface UserRepository extends CrudRepository<User, UUID> {
}
