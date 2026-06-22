package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

/**
 * Dialect for MariaDB. MariaDB is wire- and SQL-compatible with MySQL, so this dialect simply
 * extends {@link MySqlDialect} (inheriting backtick quoting, {@code ENGINE=InnoDB} table creation
 * and {@code AUTO_INCREMENT}) and overrides only where the two genuinely differ.
 *
 * <p>The one divergence is the native {@code UUID} column type, introduced in MariaDB 10.7, which
 * MySQL lacks; all other type mappings defer to the MySQL parent.
 */
public class MariaDbDialect extends MySqlDialect {

    @Override
    public String getName() {
        return "MariaDB";
    }

    /**
     * Maps {@code UUID} to MariaDB's native {@code UUID} type; everything else delegates to MySQL.
     */
    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "UUID" -> "UUID";
            default -> super.getSqlType(field);
        };
    }
}
