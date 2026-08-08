package de.tnttastisch.polydb.dialect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Dialect#getLimitClause(Long, Long)}: the standard {@code LIMIT ... OFFSET}
 * default (used by H2/MySQL/PostgreSQL/SQLite/MariaDB) and the ANSI {@code OFFSET ... FETCH} override
 * used by SQL Server, DB2 and Firebird.
 */
class DialectLimitClauseTest {

    @Test
    void standardLimitOffsetDefault() {
        Dialect dialect = new H2Dialect();
        assertThat(dialect.getLimitClause(10L, 20L)).isEqualTo("LIMIT 10 OFFSET 20");
        assertThat(dialect.getLimitClause(10L, null)).isEqualTo("LIMIT 10");
        assertThat(dialect.getLimitClause(null, 5L)).isEqualTo("OFFSET 5");
        assertThat(dialect.getLimitClause(null, null)).isEmpty();
    }

    @Test
    void sqlServerUsesOffsetFetch() {
        assertOffsetFetch(new SqlServerDialect());
    }

    @Test
    void db2UsesOffsetFetch() {
        assertOffsetFetch(new Db2Dialect());
    }

    @Test
    void firebirdUsesOffsetFetch() {
        assertOffsetFetch(new FirebirdDialect());
    }

    private static void assertOffsetFetch(Dialect dialect) {
        assertThat(dialect.getLimitClause(10L, 20L)).isEqualTo("OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY");
        assertThat(dialect.getLimitClause(10L, null)).isEqualTo("OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY");
        assertThat(dialect.getLimitClause(null, null)).isEmpty();
    }
}
