package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.query.CrudRepository;
import de.tnttastisch.polydb.query.JdbcRepository;
import de.tnttastisch.polydb.query.Repository;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.BiFunction;

/**
 * Creates runtime implementations of user-declared repository interfaces. Given an interface such as
 * {@code interface UserRepository extends CrudRepository<User, UUID>}, it resolves the entity/id types
 * (via {@link RepositoryMetadata}), builds a backing {@link JdbcRepository} for the standard CRUD
 * operations, and returns a {@link Proxy} that implements the interface — CRUD calls are delegated to
 * the backing repository, while custom query methods are routed to a {@link QueryMethodExecutor}.
 *
 * <p>The executor is supplied by a pluggable provider so richer capabilities (derived query methods,
 * {@code @Query}) can be added without changing the proxy plumbing. The default provider rejects
 * query methods via {@link QueryMethodExecutor#unsupported()}.</p>
 */
public final class RepositoryFactory {

    private final DataSource dataSource;
    private final Dialect dialect;
    private final BiFunction<CrudRepository<?, ?>, RepositoryMetadata, QueryMethodExecutor> queryMethodExecutorProvider;

    /** Creates a factory whose proxies reject any custom query method. */
    public RepositoryFactory(DataSource dataSource, Dialect dialect) {
        this(dataSource, dialect, (base, metadata) -> QueryMethodExecutor.unsupported());
    }

    /**
     * Creates a factory with a custom provider for the query-method executor.
     *
     * @param dataSource                  the pooled data source the repositories run against
     * @param dialect                     the active SQL dialect
     * @param queryMethodExecutorProvider builds the executor for a repository from its backing CRUD
     *                                    instance and resolved metadata
     */
    public RepositoryFactory(DataSource dataSource, Dialect dialect,
                             BiFunction<CrudRepository<?, ?>, RepositoryMetadata, QueryMethodExecutor> queryMethodExecutorProvider) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.queryMethodExecutorProvider = queryMethodExecutorProvider;
    }

    /**
     * Builds a proxy implementing {@code repositoryInterface}.
     *
     * @param repositoryInterface an interface extending {@link Repository}
     * @param <R>                 the repository interface type
     * @return a ready-to-use implementation of the interface
     * @throws PolyDBException if the metadata cannot be resolved (see {@link RepositoryMetadata#of})
     */
    public <R> R create(Class<R> repositoryInterface) {
        RepositoryMetadata metadata = RepositoryMetadata.of(repositoryInterface);
        CrudRepository<?, ?> base = newRepository(metadata.getEntityType());
        QueryMethodExecutor executor = queryMethodExecutorProvider.apply(base, metadata);

        InvocationHandler handler = new RepositoryInvocationHandler(repositoryInterface, base, executor);
        Object proxy = Proxy.newProxyInstance(classLoaderFor(repositoryInterface), new Class<?>[]{repositoryInterface}, handler);
        return repositoryInterface.cast(proxy);
    }

    private <E> CrudRepository<E, Object> newRepository(Class<E> entityType) {
        return new JdbcRepository<>(entityType, dataSource, dialect);
    }

    private ClassLoader classLoaderFor(Class<?> repositoryInterface) {
        ClassLoader loader = repositoryInterface.getClassLoader();
        return loader != null ? loader : getClass().getClassLoader();
    }
}
