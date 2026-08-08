package de.tnttastisch.polydb.migration.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Small internal helpers shared by the data (seed) operations to assemble parameterised DML from an
 * ordered column-to-value map. Identifiers are emitted verbatim (consistent with the rest of the DDL
 * in PolyDB); only values are parameterised, so the maps must come from trusted migration code, not
 * untrusted input.
 */
final class DataSql {

    private DataSql() {
    }

    /** {@code "a, b, c"} from the map's keys, in iteration order. */
    static String columnList(Map<String, Object> values) {
        return String.join(", ", values.keySet());
    }

    /** {@code "?, ?, ?"} with one placeholder per entry. */
    static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        return sb.toString();
    }

    /** {@code "a = ?, b = ?"} joined by {@code separator} (e.g. {@code ", "} or {@code " AND "}). */
    static String assignments(Map<String, Object> values, String separator) {
        List<String> parts = new ArrayList<>();
        for (String column : values.keySet()) {
            parts.add(column + " = ?");
        }
        return String.join(separator, parts);
    }

    /** {@code " WHERE k = ? AND ..."}, or an empty string when {@code where} is empty. */
    static String whereClause(Map<String, Object> where) {
        if (where.isEmpty()) {
            return "";
        }
        return " WHERE " + assignments(where, " AND ");
    }

    /** The map's values in iteration order; {@code null} values are preserved. */
    static List<Object> values(Map<String, Object> values) {
        return new ArrayList<>(values.values());
    }
}
