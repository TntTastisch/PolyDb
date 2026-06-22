package de.tnttastisch.polydb.core.config;

import java.util.Properties;

/**
 * Immutable configuration for a {@link de.tnttastisch.polydb.PolyDB} instance, assembled through its
 * {@link Builder}. It captures the connection coordinates, the package to scan for entities and the
 * schema-management behaviour. The connection {@link #getUrl() url} also drives dialect detection,
 * so it is the only strictly required value.
 */
public class PolyDBConfig {

    /**
     * JDBC URL (or {@code mongodb://} / {@code cassandra://} URL for NoSQL dialects). Both the
     * connection pool and the dialect are derived from this. Required.
     */
    private final String url;

    /** Database username; may be {@code null} for embedded/file databases that need no credentials. */
    private final String username;

    /** Database password; may be {@code null} when no authentication is required. */
    private final String password;

    /**
     * Fully-qualified JDBC driver class. Optional — when {@code null} the driver is resolved from the
     * URL by the pool, which suffices for modern auto-registering drivers.
     */
    private final String driverClassName;

    /**
     * Base package scanned for {@code @Entity} classes. Migrations are looked up in its
     * {@code .migrations} sub-package.
     */
    private final String entityPackage;

    /**
     * Whether PolyDB diffs the entity-derived schema against the live database and applies the
     * resulting DDL on startup. Defaults to {@code true}; set to {@code false} to manage schema
     * purely through versioned migrations.
     */
    private final boolean autoMigration;

    /** Additional, free-form properties for callers/integrations; never {@code null} (empty by default). */
    private final Properties extraProperties;

    private PolyDBConfig(Builder builder) {
        this.url = builder.url;
        this.username = builder.username;
        this.password = builder.password;
        this.driverClassName = builder.driverClassName;
        this.entityPackage = builder.entityPackage;
        this.autoMigration = builder.autoMigration;
        this.extraProperties = builder.extraProperties;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getEntityPackage() {
        return entityPackage;
    }

    public boolean isAutoMigration() {
        return autoMigration;
    }

    public Properties getExtraProperties() {
        return extraProperties;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link PolyDBConfig}; {@code autoMigration} defaults to {@code true}. */
    public static class Builder {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private String entityPackage;
        private boolean autoMigration = true;
        private Properties extraProperties = new Properties();

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder driverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        public Builder entityPackage(String entityPackage) {
            this.entityPackage = entityPackage;
            return this;
        }

        public Builder autoMigration(boolean autoMigration) {
            this.autoMigration = autoMigration;
            return this;
        }

        public Builder property(String key, String value) {
            this.extraProperties.setProperty(key, value);
            return this;
        }

        public PolyDBConfig build() {
            return new PolyDBConfig(this);
        }
    }
}
