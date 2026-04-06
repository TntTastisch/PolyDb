package de.tnttastisch.polydb.migration.core;

public interface Migration {

    String getVersion();

    String getDescription();

    void migrate(MigrationContext context) throws Exception;

}
