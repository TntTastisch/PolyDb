package de.tnttastisch.polydb.migration;

import de.tnttastisch.polydb.migration.generator.MigrationCodeGenerator;
import de.tnttastisch.polydb.migration.plan.MigrationBuilder;
import de.tnttastisch.polydb.migration.plan.MigrationPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that the auto-migration generator renders a plan into compilable {@code BaseMigration} source. */
class MigrationCodeGeneratorTest {

    @Test
    void generatesBaseMigrationSourceFromPlan() {
        MigrationBuilder m = new MigrationBuilder();
        m.createTable("users", t -> {
            t.uuidPrimaryKey("id");
            t.string("email", 100).notNull().unique();
        });
        m.addForeignKey("posts", "author_id", "users", "id");
        m.createIndex("users", "email").unique();
        MigrationPlan plan = m.build();

        String source = new MigrationCodeGenerator("com.example.migrations")
                .generate("20260101_1200", "V5_AddUsers", "add users table", plan);

        assertThat(source).contains("package com.example.migrations;");
        assertThat(source).contains("import de.tnttastisch.polydb.migration.core.BaseMigration;");
        assertThat(source).contains("class V5_AddUsers extends BaseMigration");
        assertThat(source).contains("return \"20260101_1200\";");
        assertThat(source).contains("m.createTable(\"users\"");
        assertThat(source).contains(".primaryKey()");
        assertThat(source).contains(".notNull().unique()");
        assertThat(source).contains("m.addForeignKey(\"posts\", \"author_id\", \"users\", \"id\");");
        assertThat(source).contains("m.createIndex(\"users\", \"email\").unique();");
    }
}
