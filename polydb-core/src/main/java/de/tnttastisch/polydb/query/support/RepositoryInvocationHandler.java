package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.query.CrudRepository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The {@link InvocationHandler} behind a repository proxy. It routes each invoked method to the right
 * place:
 *
 * <ul>
 *   <li>{@link Object} methods ({@code equals}, {@code hashCode}, {@code toString}) are answered from
 *       the proxy's own identity;</li>
 *   <li>{@code default} interface methods run their own body (via
 *       {@link InvocationHandler#invokeDefault});</li>
 *   <li>standard CRUD methods (anything declared on an interface the {@code target} implements, i.e.
 *       {@link CrudRepository} and its supertypes) are delegated to the backing
 *       {@link CrudRepository};</li>
 *   <li>everything else — the user's custom query methods — goes to the {@link QueryMethodExecutor}.</li>
 * </ul>
 */
final class RepositoryInvocationHandler implements InvocationHandler {

    private final Class<?> repositoryInterface;
    private final CrudRepository<?, ?> target;
    private final QueryMethodExecutor queryMethodExecutor;

    RepositoryInvocationHandler(Class<?> repositoryInterface, CrudRepository<?, ?> target, QueryMethodExecutor queryMethodExecutor) {
        this.repositoryInterface = repositoryInterface;
        this.target = target;
        this.queryMethodExecutor = queryMethodExecutor;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();

        if (declaringClass == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }

        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args == null ? new Object[0] : args);
        }

        // A method declared on an interface the backing repository implements (CrudRepository and its
        // supertypes) is standard behaviour: delegate straight to the backing instance.
        if (declaringClass.isInstance(target)) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        // Anything else is a user-declared query method.
        return queryMethodExecutor.execute(method, args == null ? new Object[0] : args);
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == (args == null ? null : args[0]);
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> repositoryInterface.getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
            default -> throw new UnsupportedOperationException("Unexpected Object method: " + method.getName());
        };
    }
}
