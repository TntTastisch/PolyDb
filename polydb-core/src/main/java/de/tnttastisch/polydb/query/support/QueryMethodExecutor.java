package de.tnttastisch.polydb.query.support;

import java.lang.reflect.Method;

/**
 * Strategy for handling repository interface methods that are <em>not</em> part of the standard CRUD
 * contract — the custom query methods a user declares, such as {@code findByUsername} or a
 * {@code @Query}-annotated method. The {@link RepositoryInvocationHandler} delegates every
 * non-CRUD, non-default, non-{@code Object} method to this executor.
 *
 * <p>This is the extension seam for later capabilities: the foundation ships
 * {@link #unsupported()}, which rejects such methods, and derived-query / {@code @Query} support
 * plug in their own executors here without touching the proxy plumbing.</p>
 */
@FunctionalInterface
public interface QueryMethodExecutor {

    /**
     * Executes a custom repository method.
     *
     * @param method the interface method being invoked
     * @param args   the call arguments (never {@code null}; an empty array for no-arg methods)
     * @return the method's result, assignable to its declared return type
     */
    Object execute(Method method, Object[] args);

    /**
     * An executor that rejects every query method. Used until derived-query support is wired in, so a
     * user who declares a finder before that capability exists gets a clear error rather than a
     * confusing proxy failure.
     *
     * @return an executor that always throws {@link UnsupportedOperationException}
     */
    static QueryMethodExecutor unsupported() {
        return (method, args) -> {
            throw new UnsupportedOperationException(
                    "Query method not supported yet: " + method.getDeclaringClass().getSimpleName()
                            + "." + method.getName()
                            + " (derived query methods and @Query are not implemented in this build)");
        };
    }
}
