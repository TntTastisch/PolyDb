package de.tnttastisch.polydb.query.sql;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders parameterised {@code SELECT}, {@code SELECT COUNT(*)} and {@code DELETE} statements from a
 * table name, an optional projection, an optional {@link Condition} tree, {@link Order ordering} and
 * limit/offset paging. Bind values live only in the {@link Condition} and are exposed via
 * {@link #parameters()} in placeholder order; the row-limiting clause is delegated to the
 * {@link Dialect} so each backend spells {@code LIMIT}/{@code OFFSET} its own way.
 *
 * <p>Replaces the earlier equality-only {@code QueryBuilder}. Identifiers (table and column names)
 * are inlined verbatim from schema metadata and are not escaped, so they must never come from
 * untrusted input; only values are bound. Builders are single-use and not thread-safe.</p>
 */
public final class SqlBuilder {

    private final String table;
    private final List<String> columns = new ArrayList<>();
    private Condition where;
    private final List<Order> orders = new ArrayList<>();
    private Long limit;
    private Long offset;

    private SqlBuilder(String table) {
        this.table = table;
    }

    /** Starts a statement against {@code table}. */
    public static SqlBuilder from(String table) {
        return new SqlBuilder(table);
    }

    /**
     * Restricts the projection to the given columns; when never called (or called with an empty list)
     * the query projects {@code *}. Calls are additive.
     */
    public SqlBuilder columns(List<String> cols) {
        if (cols != null) {
            this.columns.addAll(cols);
        }
        return this;
    }

    /** Sets the {@code WHERE} predicate; {@code null} omits the clause entirely. */
    public SqlBuilder where(Condition condition) {
        this.where = condition;
        return this;
    }

    /** Appends {@code ORDER BY} terms; {@code null} or an empty list adds nothing. */
    public SqlBuilder orderBy(List<Order> orderTerms) {
        if (orderTerms != null) {
            this.orders.addAll(orderTerms);
        }
        return this;
    }

    /** Caps the number of returned rows; {@code null} means no cap. */
    public SqlBuilder limit(Long limit) {
        this.limit = limit;
        return this;
    }

    /** Skips this many leading rows; {@code null} means no offset. */
    public SqlBuilder offset(Long offset) {
        this.offset = offset;
        return this;
    }

    /** Renders the {@code SELECT} (projection, {@code WHERE}, {@code ORDER BY}, limit/offset). */
    public String toSelectSql(Dialect dialect) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(columns.isEmpty() ? "*" : String.join(", ", columns));
        sql.append(" FROM ").append(table);
        appendWhere(sql);
        appendOrderBy(sql);
        appendLimit(sql, dialect);
        return sql.toString();
    }

    /** Renders {@code SELECT COUNT(*) FROM table [WHERE ...]}; ordering and paging are irrelevant. */
    public String toCountSql() {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table);
        appendWhere(sql);
        return sql.toString();
    }

    /** Renders {@code DELETE FROM table [WHERE ...]}. */
    public String toDeleteSql() {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);
        appendWhere(sql);
        return sql.toString();
    }

    /** The bind values for the {@code WHERE} placeholders, in placeholder order. */
    public List<Object> parameters() {
        List<Object> params = new ArrayList<>();
        if (where != null) {
            where.collectParameters(params);
        }
        return params;
    }

    private void appendWhere(StringBuilder sql) {
        if (where != null) {
            sql.append(" WHERE ").append(where.toSql());
        }
    }

    private void appendOrderBy(StringBuilder sql) {
        if (!orders.isEmpty()) {
            List<String> rendered = new ArrayList<>(orders.size());
            for (Order order : orders) {
                rendered.add(order.toSql());
            }
            sql.append(" ORDER BY ").append(String.join(", ", rendered));
        }
    }

    private void appendLimit(StringBuilder sql, Dialect dialect) {
        String clause = dialect.getLimitClause(limit, offset);
        if (clause != null && !clause.isEmpty()) {
            sql.append(" ").append(clause);
        }
    }
}
