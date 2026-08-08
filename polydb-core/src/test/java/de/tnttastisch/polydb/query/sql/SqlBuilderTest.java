package de.tnttastisch.polydb.query.sql;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.dialect.H2Dialect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SqlBuilder}: it renders {@code SELECT}/{@code COUNT}/{@code DELETE} with the
 * optional projection, {@code WHERE}, {@code ORDER BY} and dialect-provided limit/offset clause, and
 * exposes the {@code WHERE} bind values in placeholder order.
 */
class SqlBuilderTest {

    private final Dialect dialect = new H2Dialect();

    @Test
    void selectAllColumnsWhenNoProjection() {
        assertThat(SqlBuilder.from("widgets").toSelectSql(dialect)).isEqualTo("SELECT * FROM widgets");
    }

    @Test
    void selectProjectedColumns() {
        String sql = SqlBuilder.from("widgets").columns(List.of("id", "name")).toSelectSql(dialect);
        assertThat(sql).isEqualTo("SELECT id, name FROM widgets");
    }

    @Test
    void selectWithWhereAndParameters() {
        SqlBuilder builder = SqlBuilder.from("widgets").where(Condition.eq("name", "gizmo"));
        assertThat(builder.toSelectSql(dialect)).isEqualTo("SELECT * FROM widgets WHERE name = ?");
        assertThat(builder.parameters()).containsExactly("gizmo");
    }

    @Test
    void selectWithOrderBy() {
        String sql = SqlBuilder.from("widgets")
                .orderBy(List.of(Order.asc("name"), Order.desc("created_at")))
                .toSelectSql(dialect);
        assertThat(sql).isEqualTo("SELECT * FROM widgets ORDER BY name ASC, created_at DESC");
    }

    @Test
    void selectWithLimitAndOffset() {
        String sql = SqlBuilder.from("widgets").limit(10L).offset(20L).toSelectSql(dialect);
        assertThat(sql).isEqualTo("SELECT * FROM widgets LIMIT 10 OFFSET 20");
    }

    @Test
    void limitWithoutOffset() {
        assertThat(SqlBuilder.from("widgets").limit(5L).toSelectSql(dialect))
                .isEqualTo("SELECT * FROM widgets LIMIT 5");
    }

    @Test
    void fullSelectCombinesAllClausesInOrder() {
        SqlBuilder builder = SqlBuilder.from("widgets")
                .columns(List.of("id", "name"))
                .where(Condition.and(List.of(Condition.eq("active", true), Condition.gt("quantity", 3))))
                .orderBy(List.of(Order.asc("name")))
                .limit(10L)
                .offset(5L);
        assertThat(builder.toSelectSql(dialect)).isEqualTo(
                "SELECT id, name FROM widgets WHERE (active = ? AND quantity > ?) ORDER BY name ASC LIMIT 10 OFFSET 5");
        assertThat(builder.parameters()).containsExactly(true, 3);
    }

    @Test
    void countIgnoresProjectionOrderingAndPaging() {
        String sql = SqlBuilder.from("widgets")
                .columns(List.of("id"))
                .where(Condition.eq("active", true))
                .orderBy(List.of(Order.asc("name")))
                .limit(10L)
                .toCountSql();
        assertThat(sql).isEqualTo("SELECT COUNT(*) FROM widgets WHERE active = ?");
    }

    @Test
    void deleteWithWhere() {
        String sql = SqlBuilder.from("widgets").where(Condition.eq("id", 1)).toDeleteSql();
        assertThat(sql).isEqualTo("DELETE FROM widgets WHERE id = ?");
    }

    @Test
    void deleteWithoutWhere() {
        assertThat(SqlBuilder.from("widgets").toDeleteSql()).isEqualTo("DELETE FROM widgets");
    }
}
