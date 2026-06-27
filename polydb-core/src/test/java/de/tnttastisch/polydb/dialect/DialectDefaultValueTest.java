package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;
import de.tnttastisch.polydb.testentities.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the column {@code DEFAULT} resolved by the parser is rendered into DDL for both
 * {@code ALTER TABLE ... ADD} and {@code CREATE TABLE}. The placement matters: the {@code DEFAULT}
 * must precede {@code NOT NULL} so the backend can backfill existing rows, which is what makes adding
 * a non-null column to a populated table succeed.
 */
class DialectDefaultValueTest {

    private EntityModel player;

    @BeforeEach
    void setUp() {
        player = new EntityParser().parseEntity(Player.class);
    }

    private FieldModel column(String name) {
        return player.getFields().stream()
                .filter(field -> field.getColumnName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no column " + name));
    }

    @Test
    void addNotNullColumnPlacesDefaultBeforeNotNull() {
        String ddl = new PostgreSqlDialect().getAddColumnSql("players", column("notify"));
        assertThat(ddl).isEqualTo("ALTER TABLE players ADD notify BOOLEAN DEFAULT false NOT NULL");
    }

    @Test
    void addNumericColumnRendersBareLiteral() {
        String ddl = new H2Dialect().getAddColumnSql("players", column("coins"));
        assertThat(ddl).isEqualTo("ALTER TABLE players ADD coins INT DEFAULT 100 NOT NULL");
    }

    @Test
    void addNullableColumnKeepsDefaultWithoutNotNull() {
        String ddl = new PostgreSqlDialect().getAddColumnSql("players", column("nickname"));
        assertThat(ddl).isEqualTo("ALTER TABLE players ADD nickname VARCHAR(255) DEFAULT 'unknown'");
    }

    @Test
    void addColumnWithoutDefaultOmitsClause() {
        String ddl = new H2Dialect().getAddColumnSql("players", column("bio"));
        assertThat(ddl).isEqualTo("ALTER TABLE players ADD bio VARCHAR(255)");
        assertThat(ddl).doesNotContain("DEFAULT");
    }

    @Test
    void createTableInlinesDefaultsBeforeNotNull() {
        String ddl = new H2Dialect().getCreateTableSql(player.getTableName(), player.getFields(), player.getRelations());
        assertThat(ddl).contains("notify BOOLEAN DEFAULT false NOT NULL");
        assertThat(ddl).contains("coins INT DEFAULT 100 NOT NULL");
        assertThat(ddl).contains("nickname VARCHAR(255) DEFAULT 'unknown'");
        assertThat(ddl).contains("title VARCHAR(255) DEFAULT 'rookie'");
        assertThat(ddl).contains("rank VARCHAR(255) DEFAULT 'BRONZE' NOT NULL");
    }

    @Test
    void defaultClauseIsRenderedByEverySqlDialect() {
        Dialect[] dialects = {new H2Dialect(), new PostgreSqlDialect(), new MySqlDialect(), new MariaDbDialect()};
        for (Dialect dialect : dialects) {
            assertThat(dialect.getAddColumnSql("players", column("premium")))
                    .contains("DEFAULT true")
                    .endsWith("NOT NULL");
        }
    }
}
