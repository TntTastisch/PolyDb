package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;
import de.tnttastisch.polydb.testentities.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how each {@link Dialect} renders foreign-key DDL for the {@link Book} entity (whose owning
 * {@code @ManyToOne} maps to the {@code author_id} -> {@code authors(id)} constraint). Covers the
 * SQL dialects that inline the foreign key into {@code CREATE TABLE} (H2, PostgreSQL, MySQL, MariaDB,
 * SQLite), the dialect-specific column types and engine clauses, the standalone {@code ALTER TABLE}
 * form, deterministic constraint naming, and the NoSQL dialects that treat foreign keys as no-ops.
 *
 * <p>The {@link Book} entity model is parsed fresh before each test.</p>
 */
class DialectForeignKeyTest {

    /** Expected inline foreign-key clause shared across the SQL dialects under test. */
    private static final String FK_CLAUSE =
            "CONSTRAINT fk_books_author_id FOREIGN KEY (author_id) REFERENCES authors (id)";

    private EntityModel book;

    @BeforeEach
    void setUp() {
        book = new EntityParser().parseEntity(Book.class);
    }

    /** Renders the {@code CREATE TABLE} DDL for the books fixture using the given dialect. */
    private String createTable(Dialect dialect) {
        return dialect.getCreateTableSql(book.getTableName(), book.getFields(), book.getRelations());
    }

    /** H2 maps the {@code UUID} foreign-key column to a {@code UUID NOT NULL} column with an inline FK. */
    @Test
    void h2EmitsInlineForeignKeyWithUuidColumn() {
        String ddl = createTable(new H2Dialect());
        assertThat(ddl).contains("author_id UUID NOT NULL");
        assertThat(ddl).contains(FK_CLAUSE);
    }

    /** PostgreSQL uses a {@code UUID} column and inlines the same foreign-key constraint. */
    @Test
    void postgresEmitsInlineForeignKey() {
        String ddl = createTable(new PostgreSqlDialect());
        assertThat(ddl).contains("author_id UUID");
        assertThat(ddl).contains(FK_CLAUSE);
    }

    /** MySQL inlines the foreign key and appends the {@code ENGINE=InnoDB} clause required for FK support. */
    @Test
    void mySqlEmitsInlineForeignKeyAndInnoDbEngine() {
        String ddl = createTable(new MySqlDialect());
        assertThat(ddl).contains(FK_CLAUSE);
        assertThat(ddl).endsWith("ENGINE=InnoDB");
    }

    /** MariaDB inherits the MySQL behaviour: inline foreign key plus the {@code ENGINE=InnoDB} suffix. */
    @Test
    void mariaDbInheritsInnoDbAndForeignKey() {
        String ddl = createTable(new MariaDbDialect());
        assertThat(ddl).contains(FK_CLAUSE);
        assertThat(ddl).endsWith("ENGINE=InnoDB");
    }

    /**
     * SQLite stores the UUID as {@code TEXT} and inlines the foreign key, but reports that it cannot
     * add foreign keys via {@code ALTER} and exposes the {@code PRAGMA foreign_keys = ON} enable
     * statement (FK enforcement is off by default in SQLite).
     */
    @Test
    void sqliteEmitsInlineForeignKeyButCannotAlter() {
        SqliteDialect dialect = new SqliteDialect();
        String ddl = createTable(dialect);

        assertThat(ddl).contains("author_id TEXT NOT NULL");
        assertThat(ddl).contains(FK_CLAUSE);
        assertThat(dialect.supportsAddForeignKeyViaAlter()).isFalse();
        assertThat(dialect.getEnableForeignKeysStatement()).isEqualTo("PRAGMA foreign_keys = ON");
    }

    /**
     * The standalone {@code ALTER TABLE ... ADD CONSTRAINT} form (used to defer cycle-closing foreign
     * keys) produces the exact named-constraint statement.
     */
    @Test
    void alterFormProducesNamedConstraint() {
        Dialect dialect = new PostgreSqlDialect();
        String alter = dialect.getAddForeignKeySql("books", "fk_books_author_id", "author_id", "authors", "id");
        assertThat(alter)
                .isEqualTo("ALTER TABLE books ADD CONSTRAINT fk_books_author_id FOREIGN KEY (author_id) REFERENCES authors (id)");
    }

    /** Constraint names are derived deterministically as {@code fk_<table>_<column>}. */
    @Test
    void constraintNameIsDeterministic() {
        assertThat(AbstractSqlDialect.foreignKeyConstraintName("books", "author_id"))
                .isEqualTo("fk_books_author_id");
    }

    /**
     * NoSQL dialects do not support foreign keys: MongoDB reports no FK support, returns no FK
     * definition and renders {@code CREATE TABLE} as a collection-creation command; Cassandra likewise
     * reports no FK support and produces no {@code ADD FOREIGN KEY} statement.
     */
    @Test
    void noSqlDialectsTreatForeignKeysAsNoOp() {
        MongoDialect mongo = new MongoDialect();
        assertThat(mongo.supportsForeignKeys()).isFalse();
        assertThat(mongo.getForeignKeyDefinition("c", "col", "ref", "id")).isNull();
        assertThat(mongo.getCreateTableSql("books", book.getFields(), book.getRelations()))
                .isEqualTo("db.createCollection('books')");

        CassandraDialect cassandra = new CassandraDialect();
        assertThat(cassandra.supportsForeignKeys()).isFalse();
        assertThat(cassandra.getAddForeignKeySql("t", "c", "col", "ref", "id")).isNull();
    }
}
