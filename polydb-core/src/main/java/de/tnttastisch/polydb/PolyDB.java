package de.tnttastisch.polydb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.tnttastisch.polydb.core.config.PolyDBConfig;
import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.dialect.*;
import de.tnttastisch.polydb.migration.core.MigrationContext;
import de.tnttastisch.polydb.migration.core.MigrationRunner;
import de.tnttastisch.polydb.query.CrudRepository;
import de.tnttastisch.polydb.query.JdbcRepository;
import de.tnttastisch.polydb.query.Repository;
import de.tnttastisch.polydb.query.support.RepositoryFactory;
import de.tnttastisch.polydb.schema.comparison.SchemaChange;
import de.tnttastisch.polydb.schema.comparison.SchemaComparator;
import de.tnttastisch.polydb.schema.db.DatabaseSchema;
import de.tnttastisch.polydb.schema.db.DatabaseSchemaReader;
import de.tnttastisch.polydb.schema.generator.SchemaGenerator;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * Main entry point and lifecycle owner of the library. A {@code PolyDB} instance wraps a single
 * database connection (a Hikari-pooled {@link DataSource} for JDBC dialects) together with the
 * {@link Dialect} detected from the configured URL.
 *
 * <p>Typical lifecycle:</p>
 * <ol>
 *   <li><b>Build / configure</b> — assemble a {@link PolyDBConfig} (via {@link #builder()} or
 *       {@link PolyDBConfig#builder()}).</li>
 *   <li><b>Start</b> — {@link #start(PolyDBConfig)} detects the dialect, opens the pool and runs
 *       {@link #initialize()}: it parses the entity package, optionally syncs the schema (when
 *       {@code autoMigration} is enabled) and then applies pending versioned migrations.</li>
 *   <li><b>Use</b> — obtain a {@link Repository} per entity type via {@link #repository(Class)}.</li>
 *   <li><b>Close</b> — {@link #close()} (or try-with-resources) shuts down the pool. The instance is
 *       single-use; once closed it cannot be reopened.</li>
 * </ol>
 *
 * <p>NoSQL dialects (MongoDB, Cassandra) are detected but currently have no {@code DataSource} and no
 * repository implementation; calling {@link #repository(Class)} on them throws.</p>
 */
public class PolyDB implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PolyDB.class);

    private final PolyDBConfig config;
    private final Dialect dialect;
    private final DataSource dataSource;

    /** Builds proxy implementations of user-declared repository interfaces; {@code null} for NoSQL. */
    private final RepositoryFactory repositoryFactory;

    /**
     * Optional handle to a non-JDBC native client (e.g. a future {@code MongoClient} or
     * {@code CqlSession}). Such clients are not {@link DataSource}s, so they are tracked separately
     * and closed in {@link #close()} when present.
     */
    private AutoCloseable nativeClient;

    private volatile boolean closed;

    private PolyDB(PolyDBConfig config) {
        this.config = config;
        this.dialect = detectDialect(config);
        this.dataSource = createDataSource(config, dialect);
        this.repositoryFactory = dataSource != null ? new RepositoryFactory(dataSource, dialect) : null;
    }

    /**
     * Creates and fully initializes an instance from the given configuration: detects the dialect,
     * opens the connection pool and runs schema sync + migrations. The returned instance is ready to
     * hand out repositories.
     *
     * @throws PolyDBException if the URL maps to an unsupported dialect or schema sync fails
     */
    public static PolyDB start(PolyDBConfig config) {
        PolyDB polyDB = new PolyDB(config);
        polyDB.initialize();
        return polyDB;
    }

    /**
     * Closes the underlying connection pool (and any registered native client). Idempotent: calling
     * it more than once is a no-op.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        try {
            closeDataSource();
        } finally {
            closeNativeClient();
        }
    }

    /**
     * Alias for {@link #close()}; {@code close()} remains the idiomatic name and enables
     * try-with-resources.
     */
    public void shutdown() {
        close();
    }

    /** Whether {@link #close()} has been called on this instance. */
    public boolean isClosed() {
        return closed;
    }

    private void closeDataSource() {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
            return;
        }

        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new PolyDBException("Failed to close datasource", e);
            }
        }
    }

    private void closeNativeClient() {
        if (nativeClient != null) {
            try {
                nativeClient.close();
            } catch (Exception e) {
                throw new PolyDBException("Failed to close native client", e);
            }
        }
    }

    /**
     * Registers a native (non-JDBC) client to be closed alongside this instance. Reserved for the
     * NoSQL implementations.
     */
    protected void registerNativeClient(AutoCloseable nativeClient) {
        this.nativeClient = nativeClient;
    }

    /** Convenience entry point: a fluent builder that configures and {@link Builder#start() starts} PolyDB in one chain. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder that delegates to {@link PolyDBConfig.Builder} and starts the instance directly,
     * so callers can configure and launch PolyDB without handling a {@link PolyDBConfig} explicitly.
     */
    public static class Builder {
        private final PolyDBConfig.Builder configBuilder = PolyDBConfig.builder();

        /** Sets the connection URL; drives both pooling and dialect detection. */
        public Builder url(String url) {
            configBuilder.url(url);
            return this;
        }

        /** Sets the database username. */
        public Builder username(String username) {
            configBuilder.username(username);
            return this;
        }

        /** Sets the database password. */
        public Builder password(String password) {
            configBuilder.password(password);
            return this;
        }

        /** Sets an explicit JDBC driver class; usually unnecessary with auto-registering drivers. */
        public Builder driverClassName(String driverClassName) {
            configBuilder.driverClassName(driverClassName);
            return this;
        }

        /** Sets the base package scanned for entities (and, under {@code .migrations}, for migrations). */
        public Builder entityPackage(String entityPackage) {
            configBuilder.entityPackage(entityPackage);
            return this;
        }

        /** Enables or disables automatic schema sync on startup (default {@code true}). */
        public Builder autoMigration(boolean autoMigration) {
            configBuilder.autoMigration(autoMigration);
            return this;
        }

        /** Builds the configuration and {@linkplain PolyDB#start(PolyDBConfig) starts} a ready-to-use instance. */
        public PolyDB start() {
            return PolyDB.start(configBuilder.build());
        }
    }

    /**
     * Runs the startup pipeline: parse entities, optionally diff and apply schema changes, then run
     * versioned migrations. Invoked once from {@link #start(PolyDBConfig)}.
     */
    private void initialize() {
        log.info("Initializing PolyDB...");

        EntityParser parser = new EntityParser();
        List<EntityModel> entities = parser.parsePackage(config.getEntityPackage());
        log.info("Found {} entities", entities.size());

        // Skip schema sync when disabled or when there is no JDBC data source (NoSQL), but still run
        // the migration step (which itself no-ops without a data source).
        if (!config.isAutoMigration() || dataSource == null) {
            runMigrations();
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseSchemaReader reader = new DatabaseSchemaReader();
            DatabaseSchema dbSchema = reader.readSchema(conn);

            SchemaComparator comparator = new SchemaComparator();
            List<SchemaChange> changes = comparator.compare(entities, dbSchema);

            if (changes.isEmpty()) {
                log.info("Schema is up to date");
                runMigrations();
                return;
            }

            log.info("Detected {} schema changes, applying...", changes.size());
            // Translate the dialect-agnostic changes into concrete DDL and apply them sequentially.
            SchemaGenerator generator = new SchemaGenerator(dialect);
            List<String> sqls = generator.generateSql(changes);

            try (Statement stmt = conn.createStatement()) {
                for (String sql : sqls) {
                    log.debug("Executing: {}", sql);
                    stmt.execute(sql);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync schema", e);
            throw new PolyDBException("Failed to sync schema", e);
        }

        runMigrations();
    }

    /** Applies pending versioned migrations from the entity package's {@code .migrations} sub-package. */
    private void runMigrations() {
        MigrationContext migrationContext = new MigrationContext(dataSource, dialect);
        MigrationRunner migrationRunner = new MigrationRunner(migrationContext);
        migrationRunner.run(config.getEntityPackage() + ".migrations");

        log.info("PolyDB is ready");
    }

    private DataSource createDataSource(PolyDBConfig config, Dialect dialect) {
        String url = config.getUrl().toLowerCase();
        if (url.startsWith("mongodb://") || url.startsWith("cassandra://") || url.contains(":cassandra:")) {
            return null;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        if (config.getDriverClassName() != null) {
            hikariConfig.setDriverClassName(config.getDriverClassName());
        }

        // Per-connection setup such as SQLite's "PRAGMA foreign_keys = ON" so foreign-key
        // enforcement is active on every pooled connection.
        String connectionInit = dialect.getEnableForeignKeysStatement();
        if (connectionInit != null) {
            hikariConfig.setConnectionInitSql(connectionInit);
        }

        return new HikariDataSource(hikariConfig);
    }

    private Dialect detectDialect(PolyDBConfig config) {
        String url = config.getUrl().toLowerCase();
        String protocol = extractProtocol(url);

        return switch (protocol) {
            case "h2" -> new H2Dialect();
            case "mysql" -> new MySqlDialect();
            case "mariadb" -> new MariaDbDialect();
            case "postgresql" -> new PostgreSqlDialect();
            case "sqlite" -> new SqliteDialect();
            case "oracle" -> new OracleDialect();
            case "sqlserver" -> new SqlServerDialect();
            case "firebird" -> new FirebirdDialect();
            case "db2" -> new Db2Dialect();
            case "mongodb" -> new MongoDialect();
            case "cassandra" -> new CassandraDialect();
            default -> throw new PolyDBException("Unsupported database dialect for URL: " + config.getUrl());
        };
    }

    private String extractProtocol(String url) {
        if (url.startsWith("mongodb://")) return "mongodb";
        if (url.startsWith("cassandra://") || url.contains(":cassandra:")) return "cassandra";

        if (url.startsWith("jdbc:")) {
            String[] parts = url.split(":");
            if (parts.length > 1) {
                return parts[1];
            }
        }

        return "unknown";
    }

    /**
     * Returns a generic {@link CrudRepository} straight from an entity class, backed by this
     * instance's data source and dialect. This is the quick path when you do not need custom finder
     * methods; the identifier type is left as {@link Object}. For type-safe ids and custom query
     * methods, declare a repository interface and use {@link #getRepository(Class)} instead.
     *
     * <p>A new repository is created per call (they are lightweight); the entity is parsed on
     * construction.</p>
     *
     * @param entityClass the {@code @Entity} class to manage
     * @param <T>         the entity type
     * @return a CRUD repository for the entity
     * @throws IllegalStateException         if this instance has been closed
     * @throws UnsupportedOperationException for NoSQL dialects, which have no repository support yet
     */
    public <T> CrudRepository<T, Object> repository(Class<T> entityClass) {
        ensureOpen();
        requireJdbc();
        return new JdbcRepository<>(entityClass, dataSource, dialect);
    }

    /**
     * Returns an implementation of a user-declared repository interface, synthesised at runtime via a
     * dynamic proxy. The interface must extend {@link Repository} (usually {@link CrudRepository}); its
     * entity and id types are resolved from the generic declaration. Standard CRUD methods are backed
     * by a {@link JdbcRepository}; custom query methods are handled by the repository infrastructure.
     *
     * <p>Example:</p>
     * <pre>{@code
     * interface UserRepository extends CrudRepository<User, UUID> { }
     * UserRepository users = polyDB.getRepository(UserRepository.class);
     * }</pre>
     *
     * @param repositoryInterface the repository interface to implement
     * @param <R>                 the repository interface type
     * @return a ready-to-use implementation
     * @throws IllegalStateException         if this instance has been closed
     * @throws UnsupportedOperationException for NoSQL dialects, which have no repository support yet
     */
    public <R extends Repository<?, ?>> R getRepository(Class<R> repositoryInterface) {
        ensureOpen();
        requireJdbc();
        return repositoryFactory.create(repositoryInterface);
    }

    /** Guards repository access against NoSQL dialects, which have no data source or proxy support. */
    private void requireJdbc() {
        if (dataSource == null) {
            throw new UnsupportedOperationException("NoSQL repositories not yet implemented");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("PolyDB has been closed");
        }
    }

    /** The underlying connection pool, or {@code null} for NoSQL dialects. Exposed for advanced/JDBC access. */
    public DataSource getDataSource() {
        return dataSource;
    }

    /** The dialect detected from the configured URL. */
    public Dialect getDialect() {
        return dialect;
    }
}
