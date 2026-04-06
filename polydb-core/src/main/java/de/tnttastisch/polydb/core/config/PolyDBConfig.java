package de.tnttastisch.polydb.core.config;

import java.util.Properties;

public class PolyDBConfig {

    private final String url;
    private final String username;
    private final String password;
    private final String driverClassName;
    private final String entityPackage;
    private final boolean autoMigration;
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
