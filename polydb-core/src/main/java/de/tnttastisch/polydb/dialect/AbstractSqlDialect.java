package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;
import de.tnttastisch.polydb.schema.model.RelationModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for SQL dialects. Provides standards-based DDL generation shared across vendors:
 * {@code CREATE TABLE} assembly (column definitions, {@code NOT NULL}, auto-increment, {@code UNIQUE},
 * primary key and inline foreign-key constraints), the {@code ALTER TABLE} variants, double-quoted
 * identifier quoting, and full foreign-key support enabled by default.
 *
 * <p>Concrete dialects supply their vendor's {@linkplain #getSqlType(FieldModel) type mappings} and
 * {@linkplain #getAutoIncrementKeyword() auto-increment keyword}, and override only the statements
 * their backend spells differently (most commonly {@link #getModifyColumnSql} and identifier
 * quoting).
 */
public abstract class AbstractSqlDialect implements Dialect {

    @Override
    public String getCreateTableSql(String tableName, List<FieldModel> fields, List<RelationModel> relations) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (\n");

        List<String> definitions = new ArrayList<>();
        for (FieldModel field : fields) {
            StringBuilder def = new StringBuilder("  ");
            def.append(field.getColumnName()).append(" ").append(getSqlType(field));
            def.append(defaultClause(field));
            if (!field.isNullable()) {
                def.append(" NOT NULL");
            }
            if (field.isAutoIncrement()) {
                def.append(" ").append(getAutoIncrementKeyword());
            }
            if (field.isUnique() && !field.isId()) {
                def.append(" UNIQUE");
            }
            definitions.add(def.toString());
        }

        List<String> pkColumns = fields.stream()
                .filter(FieldModel::isId)
                .map(FieldModel::getColumnName)
                .collect(Collectors.toList());

        if (!pkColumns.isEmpty()) {
            definitions.add("  PRIMARY KEY (" + String.join(", ", pkColumns) + ")");
        }

        if (supportsForeignKeys() && relations != null) {
            for (RelationModel relation : relations) {
                if (isInlineForeignKey(relation)) {
                    String constraintName = foreignKeyConstraintName(tableName, relation.getJoinColumnName());
                    definitions.add("  " + getForeignKeyDefinition(
                            constraintName,
                            relation.getJoinColumnName(),
                            relation.getReferencedTable(),
                            relation.getReferencedColumnName()));
                }
            }
        }

        sql.append(String.join(",\n", definitions));
        sql.append("\n)");
        return sql.toString();
    }

    /**
     * An owning relation that owns a single foreign-key column on this table (many-to-one or owning
     * one-to-one). Many-to-many relations carry their foreign keys on a separate join table.
     */
    private boolean isInlineForeignKey(RelationModel relation) {
        return relation.isOwningSide() && relation.getJoinColumnName() != null;
    }

    /**
     * The keyword appended to an auto-increment column definition (e.g. {@code AUTO_INCREMENT},
     * {@code IDENTITY(1,1)}, {@code GENERATED ALWAYS AS IDENTITY}). Returns an empty string for
     * dialects that encode auto-increment in the column type instead (e.g. PostgreSQL's
     * {@code SERIAL}).
     */
    protected abstract String getAutoIncrementKeyword();

    @Override
    public String getAddColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ADD " + field.getColumnName() + " " + getSqlType(field) +
                defaultClause(field) + (field.isNullable() ? "" : " NOT NULL");
    }

    /**
     * Renders a fixed-point decimal type ({@code BigDecimal}/{@code BigInteger}) using the column's
     * declared {@link FieldModel#getPrecision() precision} and {@link FieldModel#getScale() scale}.
     * When no precision is declared ({@code 0}) the bare {@code keyword} is returned so the backend
     * applies its own default precision and scale.
     *
     * @param keyword the vendor's fixed-point type keyword (e.g. {@code DECIMAL}, {@code NUMERIC},
     *                {@code NUMBER})
     * @param field   the column whose precision/scale parameterise the type
     */
    protected String decimalType(String keyword, FieldModel field) {
        if (field.getPrecision() <= 0) {
            return keyword;
        }
        int scale = Math.max(field.getScale(), 0);
        return keyword + "(" + field.getPrecision() + ", " + scale + ")";
    }

    /**
     * Renders the {@code DEFAULT} clause for a column, or an empty string when it declares no
     * default. The value is emitted verbatim (see
     * {@link de.tnttastisch.polydb.core.annotations.Column#defaultValue()}); supplying one is what
     * lets a {@code NOT NULL} column be added to a populated table, as the backend backfills the
     * existing rows with it instead of rejecting the {@code ALTER} for null values.
     */
    protected String defaultClause(FieldModel field) {
        return field.hasDefault() ? " DEFAULT " + field.getDefaultValue() : "";
    }

    @Override
    public String getDropColumnSql(String tableName, String columnName) {
        return "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
    }

    @Override
    public String getCreateIndexSql(String tableName, IndexModel index) {
        String unique = index.isUnique() ? "UNIQUE " : "";
        String indexName = index.getName().isEmpty() ? "idx_" + tableName + "_" + String.join("_", index.getColumns()) : index.getName();
        return "CREATE " + unique + "INDEX " + indexName + " ON " + tableName + " (" + String.join(", ", index.getColumns()) + ")";
    }

    @Override
    public String getDropIndexSql(String tableName, String indexName) {
        return "DROP INDEX " + indexName;
    }

    @Override
    public String getDropTableSql(String tableName) {
        return "DROP TABLE " + tableName;
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " MODIFY " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? "" : " NOT NULL");
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return "\"" + identifier + "\"";
    }

    // ------------------------------------------------------------------ foreign keys

    @Override
    public boolean supportsForeignKeys() {
        return true;
    }

    @Override
    public boolean supportsAddForeignKeyViaAlter() {
        return true;
    }

    @Override
    public String getForeignKeyDefinition(String constraintName, String column, String refTable, String refColumn) {
        return "CONSTRAINT " + constraintName + " FOREIGN KEY (" + column + ") REFERENCES " + refTable + " (" + refColumn + ")";
    }

    @Override
    public String getAddForeignKeySql(String tableName, String constraintName, String column, String refTable, String refColumn) {
        return "ALTER TABLE " + tableName + " ADD CONSTRAINT " + constraintName +
                " FOREIGN KEY (" + column + ") REFERENCES " + refTable + " (" + refColumn + ")";
    }

    @Override
    public String getEnableForeignKeysStatement() {
        return null;
    }

    /**
     * Deterministic, reproducible constraint name so a foreign key can be added inline at creation
     * time and later detected / dropped.
     */
    public static String foreignKeyConstraintName(String tableName, String column) {
        return "fk_" + tableName + "_" + column;
    }
}
