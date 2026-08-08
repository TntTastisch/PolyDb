package de.tnttastisch.polydb.migration.generator;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.migration.operation.AddColumnOperation;
import de.tnttastisch.polydb.migration.operation.AddForeignKeyOperation;
import de.tnttastisch.polydb.migration.operation.CreateIndexOperation;
import de.tnttastisch.polydb.migration.operation.CreateTableOperation;
import de.tnttastisch.polydb.migration.operation.MigrationOperation;
import de.tnttastisch.polydb.migration.plan.MigrationPlan;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders a {@link MigrationPlan} (typically one produced by the schema comparator from an entity
 * change) into the Java source of a {@link de.tnttastisch.polydb.migration.core.BaseMigration}, using
 * fluent {@code MigrationBuilder} calls. This turns automatic migration into a <em>generator</em>: a
 * developer reviews and versions the emitted file, so production systems can run migrations only, with
 * no automatic schema changes.
 */
public final class MigrationCodeGenerator {

    private final String targetPackage;

    public MigrationCodeGenerator(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    /** Generates the Java source of a migration class for the given plan. */
    public String generate(String version, String className, String description, MigrationPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(targetPackage).append(";\n\n");
        sb.append("import de.tnttastisch.polydb.migration.core.BaseMigration;\n");
        sb.append("import de.tnttastisch.polydb.migration.plan.MigrationBuilder;\n\n");
        sb.append("/**\n * Generated migration. Review before committing.\n */\n");
        sb.append("public class ").append(className).append(" extends BaseMigration {\n\n");
        sb.append("    @Override\n");
        sb.append("    public String getVersion() {\n        return \"").append(escape(version)).append("\";\n    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public String getDescription() {\n        return \"").append(escape(description)).append("\";\n    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void up(MigrationBuilder m) {\n");
        for (MigrationOperation operation : plan.getOperations()) {
            sb.append(render(operation));
        }
        sb.append("    }\n}\n");
        return sb.toString();
    }

    /** Generates the source and writes it to {@code <directory>/<className>.java}. */
    public Path writeTo(Path directory, String version, String className, String description, MigrationPlan plan) {
        String source = generate(version, className, description, plan);
        Path file = directory.resolve(className + ".java");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, source, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new PolyDBException("Failed to write generated migration to " + file, e);
        }
    }

    private String render(MigrationOperation operation) {
        if (operation instanceof CreateTableOperation create) {
            return renderCreateTable(create);
        }
        if (operation instanceof AddColumnOperation add) {
            return "        " + columnChain("m.addColumn(\"" + add.tableName() + "\", \"" +
                    add.field().getColumnName() + "\", " + typeLiteral(add.field()), add.field()) + ";\n";
        }
        if (operation instanceof AddForeignKeyOperation fk) {
            return "        m.addForeignKey(\"" + fk.tableName() + "\", \"" + fk.column() + "\", \"" +
                    fk.referencedTable() + "\", \"" + fk.referencedColumn() + "\");\n";
        }
        if (operation instanceof CreateIndexOperation index) {
            return renderCreateIndex(index);
        }
        // Operations the generator does not model as builder calls are left as a reviewable marker.
        return "        // TODO review: " + operation.describe() + "\n";
    }

    private String renderCreateTable(CreateTableOperation create) {
        StringBuilder sb = new StringBuilder();
        sb.append("        m.createTable(\"").append(create.tableName()).append("\", t -> {\n");
        for (FieldModel field : create.fields()) {
            sb.append("            ")
                    .append(columnChain("t.column(\"" + field.getColumnName() + "\", " + typeLiteral(field), field))
                    .append(";\n");
        }
        sb.append("        });\n");
        return sb.toString();
    }

    private String renderCreateIndex(CreateIndexOperation index) {
        IndexModel model = index.index();
        StringBuilder columns = new StringBuilder();
        for (int i = 0; i < model.getColumns().size(); i++) {
            if (i > 0) columns.append(", ");
            columns.append("\"").append(model.getColumns().get(i)).append("\"");
        }
        StringBuilder sb = new StringBuilder("        m.createIndex(\"" + index.tableName() + "\", " + columns + ")");
        if (model.isUnique()) {
            sb.append(".unique()");
        }
        if (model.getName() != null && !model.getName().isEmpty()) {
            sb.append(".name(\"").append(model.getName()).append("\")");
        }
        sb.append(";\n");
        return sb.toString();
    }

    /** Appends the fluent constraint calls for a column onto {@code head} (which opens the column call). */
    private String columnChain(String head, FieldModel field) {
        StringBuilder sb = new StringBuilder(head).append(")");
        if (field.isId()) {
            sb.append(".primaryKey()");
        } else if (!field.isNullable()) {
            sb.append(".notNull()");
        }
        if (field.isAutoIncrement()) {
            sb.append(".autoIncrement()");
        }
        if (field.isUnique() && !field.isId()) {
            sb.append(".unique()");
        }
        if (field.getType() == String.class && field.getLength() != 255) {
            sb.append(".length(").append(field.getLength()).append(")");
        }
        if (field.getPrecision() > 0) {
            sb.append(".precision(").append(field.getPrecision()).append(").scale(").append(field.getScale()).append(")");
        }
        if (field.hasDefault()) {
            sb.append(".defaultValue(\"").append(escape(field.getDefaultValue())).append("\")");
        }
        return sb.toString();
    }

    private String typeLiteral(FieldModel field) {
        Class<?> type = field.getType();
        String name = type == null ? null : type.getCanonicalName();
        return (name == null ? "Object" : name) + ".class";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
