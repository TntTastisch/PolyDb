package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.query.Repository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves, by reflection, which entity type and identifier type a user-declared repository interface
 * serves. It walks the generic interface hierarchy from the declared repository up to the root
 * {@link Repository} marker, carrying type-variable bindings through each level, so an interface such
 * as {@code interface UserRepository extends CrudRepository<User, UUID>} resolves to
 * {@code (User, UUID)} regardless of how many intermediate interfaces sit in between.
 */
public final class RepositoryMetadata {

    private final Class<?> repositoryInterface;
    private final Class<?> entityType;
    private final Class<?> idType;

    private RepositoryMetadata(Class<?> repositoryInterface, Class<?> entityType, Class<?> idType) {
        this.repositoryInterface = repositoryInterface;
        this.entityType = entityType;
        this.idType = idType;
    }

    /**
     * Derives the metadata for {@code repositoryInterface}.
     *
     * @param repositoryInterface an interface extending {@link Repository}
     * @return the resolved entity and id types
     * @throws PolyDBException if the argument is not an interface extending {@link Repository}, or its
     *                         type arguments cannot be resolved to concrete classes
     */
    public static RepositoryMetadata of(Class<?> repositoryInterface) {
        if (repositoryInterface == null || !repositoryInterface.isInterface()) {
            throw new PolyDBException("Repository type must be an interface: " + repositoryInterface);
        }
        if (!Repository.class.isAssignableFrom(repositoryInterface)) {
            throw new PolyDBException(repositoryInterface.getName() + " must extend " + Repository.class.getName());
        }
        Class<?>[] arguments = resolve(repositoryInterface, new HashMap<>());
        if (arguments == null) {
            throw new PolyDBException("Could not resolve entity/id types for " + repositoryInterface.getName()
                    + "; declare them explicitly, e.g. extends CrudRepository<MyEntity, MyId>");
        }
        return new RepositoryMetadata(repositoryInterface, arguments[0], arguments[1]);
    }

    public Class<?> getRepositoryInterface() {
        return repositoryInterface;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public Class<?> getIdType() {
        return idType;
    }

    /**
     * Recursively searches {@code type}'s generic interface graph for the {@link Repository} root,
     * resolving its two type arguments against the accumulated variable {@code bindings}. Returns
     * {@code [entityType, idType]}, or {@code null} if this branch does not reach {@link Repository}.
     */
    private static Class<?>[] resolve(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof Class<?> clazz) {
            for (Type genericInterface : clazz.getGenericInterfaces()) {
                Class<?>[] resolved = resolve(genericInterface, bindings);
                if (resolved != null) {
                    return resolved;
                }
            }
            return null;
        }

        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Map<TypeVariable<?>, Type> childBindings = bind(raw, parameterized, bindings);

            if (raw == Repository.class) {
                TypeVariable<?>[] vars = raw.getTypeParameters();
                return new Class<?>[]{
                        toClass(childBindings.get(vars[0]), raw),
                        toClass(childBindings.get(vars[1]), raw)
                };
            }

            for (Type genericInterface : raw.getGenericInterfaces()) {
                Class<?>[] resolved = resolve(genericInterface, childBindings);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        return null;
    }

    /** Maps {@code raw}'s declared type variables to the actual arguments of {@code parameterized}. */
    private static Map<TypeVariable<?>, Type> bind(Class<?> raw, ParameterizedType parameterized, Map<TypeVariable<?>, Type> bindings) {
        Map<TypeVariable<?>, Type> childBindings = new HashMap<>();
        TypeVariable<?>[] vars = raw.getTypeParameters();
        Type[] actuals = parameterized.getActualTypeArguments();
        for (int i = 0; i < vars.length; i++) {
            childBindings.put(vars[i], resolveVariable(actuals[i], bindings));
        }
        return childBindings;
    }

    /** Follows a type variable through the outer {@code bindings}; leaves concrete types untouched. */
    private static Type resolveVariable(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            Type bound = bindings.get(variable);
            return bound != null ? bound : type;
        }
        return type;
    }

    private static Class<?> toClass(Type type, Class<?> owner) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        throw new PolyDBException("Could not resolve a concrete type argument for " + owner.getName()
                + " (got " + type + "); repository type parameters must be concrete classes");
    }
}
