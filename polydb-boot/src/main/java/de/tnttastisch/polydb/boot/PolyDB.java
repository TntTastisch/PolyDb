package de.tnttastisch.polydb.boot;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.tnttastisch.polydb.core.config.PolyDBConfig;
import de.tnttastisch.polydb.dialect.*;
import de.tnttastisch.polydb.migration.core.MigrationContext;
import de.tnttastisch.polydb.migration.core.MigrationRunner;
import de.tnttastisch.polydb.query.JdbcRepository;
import de.tnttastisch.polydb.query.Repository;
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

public class PolyDB {

    private static final Logger log = LoggerFactory.getLogger(PolyDB.class);

    private final PolyDBConfig config;
    private final DataSource dataSource;
    private final Dialect dialect;

    private PolyDB(PolyDBConfig config) {
        this.config = config;
        this.dataSource = createDataSource(config);
        this.dialect = detectDialect(config);
    }

    public static PolyDB start(PolyDBConfig config) {
        PolyDB polyDB = new PolyDB(config);
        polyDB.initialize();
        return polyDB;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PolyDBConfig.Builder configBuilder = PolyDBConfig.builder();

        public Builder url(String url) {
            configBuilder.url(url);
            return this;
        }

        public Builder username(String username) {
            configBuilder.username(username);
            return this;
        }

        public Builder password(String password) {
            configBuilder.password(password);
            return this;
        }

        public Builder driverClassName(String driverClassName) {
            configBuilder.driverClassName(driverClassName);
            return this;
        }

        public Builder entityPackage(String entityPackage) {
            configBuilder.entityPackage(entityPackage);
            return this;
        }

        public Builder autoMigration(boolean autoMigration) {
            configBuilder.autoMigration(autoMigration);
            return this;
        }

        public PolyDB start() {
            return PolyDB.start(configBuilder.build());
        }
    }

    private void initialize() {
        log.info("Initializing PolyDB...");

        EntityParser parser = new EntityParser();
        List<EntityModel> entities = parser.parsePackage(config.getEntityPackage());
        log.info("Found {} entities", entities.size());

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
            throw new RuntimeException(e);
        }

        runMigrations();
    }

    private void runMigrations() {
        MigrationContext migrationContext = new MigrationContext(dataSource, dialect);
        MigrationRunner migrationRunner = new MigrationRunner(migrationContext);
        migrationRunner.run(config.getEntityPackage() + ".migrations");

        log.info("PolyDB is ready");
    }

    private DataSource createDataSource(PolyDBConfig config) {
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

        return new HikariDataSource(hikariConfig);
    }

    private Dialect detectDialect(PolyDBConfig config) {
        String url = config.getUrl().toLowerCase();
        String protocol = extractProtocol(url);

        switch (protocol) {
            case "h2": return new H2Dialect();
            case "mysql": return new MySqlDialect();
            case "mariadb": return new MariaDbDialect();
            case "postgresql": return new PostgreSqlDialect();
            case "sqlite": return new SqliteDialect();
            case "oracle": return new OracleDialect();
            case "sqlserver": return new SqlServerDialect();
            case "firebird": return new FirebirdDialect();
            case "db2": return new Db2Dialect();
            case "mongodb": return new MongoDialect();
            case "cassandra": return new CassandraDialect();
            default: throw new RuntimeException("Unsupported database dialect for URL: " + config.getUrl());
        }
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

    public <T> Repository<T> repository(Class<T> entityClass) {
        if (dataSource != null) {
            return new JdbcRepository<>(entityClass, dataSource, dialect);
        }
        throw new UnsupportedOperationException("NoSQL repositories not yet implemented");
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Dialect getDialect() {
        return dialect;
    }
}
