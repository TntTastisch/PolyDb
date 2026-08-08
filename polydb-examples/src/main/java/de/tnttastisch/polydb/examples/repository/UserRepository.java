package de.tnttastisch.polydb.examples.repository;

import de.tnttastisch.polydb.examples.entity.User;
import de.tnttastisch.polydb.query.PagingAndSortingRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Example of a user-declared repository interface. By extending {@link PagingAndSortingRepository} with
 * the entity type ({@link User}) and its id type ({@link UUID}), it inherits the full standard CRUD
 * surface plus sorting and pagination with no implementation class: PolyDB synthesises the
 * implementation at runtime (see {@code polyDB.getRepository(UserRepository.class)}).
 *
 * <p>This is the type-safe counterpart to the quick {@code polyDB.repository(User.class)} path — the
 * id type is {@code UUID} here rather than {@code Object}. It also declares <em>derived query
 * methods</em>: PolyDB reads the method names and generates the SQL, so no bodies are needed.</p>
 */
public interface UserRepository extends PagingAndSortingRepository<User, UUID> {

    /** Looks up a single user by exact username. */
    Optional<User> findByUsername(String username);

    /** Case-insensitive substring search over usernames. */
    List<User> findByUsernameContainingIgnoreCase(String part);

    /** Counts how many users carry the given email. */
    long countByEmail(String email);
}
