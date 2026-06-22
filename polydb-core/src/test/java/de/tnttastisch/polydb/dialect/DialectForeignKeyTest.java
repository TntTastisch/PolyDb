package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;
import de.tnttastisch.polydb.testentities.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialectForeignKeyTest {

    private static final String FK_CLAUSE =
            "CONSTRAINT fk_books_author_id FOREIGN KEY (author_id) REFERENCES authors (id)";

    private EntityModel book;

    @BeforeEach
    void setUp() {
        book = new EntityParser().parseEntity(Book.class);
    }

    private String createTable(Dialect dialect) {
        return dialect.getCreateTableSql(book.getTableName(), book.getFields(), book.getRelations());
    }

    @Test
    void h2EmitsInlineForeignKeyWithUuidColumn() {
        String ddl = createTable(new H2Dialect());
        assertThat(ddl).contains("author_id UUID NOT NULL");
        assertThat(ddl).contains(FK_CLAUSE);
    }

    @Test
    void postgresEmitsInlineForeignKey() {
        String ddl = createTable(new PostgreSqlDialect());
        assertThat(ddl).contains("author_id UUID");
        assertThat(ddl).contains(FK_CLAUSE);
    }

    @Test
    void mySqlEmitsInlineForeignKeyAndInnoDbEngine() {
        String ddl = createTable(new MySqlDialect());
        assertThat(ddl).contains(FK_CLAUSE);
        assertThat(ddl).endsWith("ENGINE=InnoDB");
    }

    @Test
    void mariaDbInheritsInnoDbAndForeignKey() {
        String ddl = createTable(new MariaDbDialect());
        assertThat(ddl).contains(FK_CLAUSE);
        assertThat(ddl).endsWith("ENGINE=InnoDB");
    }

    @Test
    void sqliteEmitsInlineForeignKeyButCannotAlter() {
        SqliteDialect dialect = new SqliteDialect();
        String ddl = createTable(dialect);

        assertThat(ddl).contains("author_id TEXT NOT NULL");
        assertThat(ddl).contains(FK_CLAUSE);
        assertThat(dialect.supportsAddForeignKeyViaAlter()).isFalse();
        assertThat(dialect.getEnableForeignKeysStatement()).isEqualTo("PRAGMA foreign_keys = ON");
    }

    @Test
    void alterFormProducesNamedConstraint() {
        Dialect dialect = new PostgreSqlDialect();
        String alter = dialect.getAddForeignKeySql("books", "fk_books_author_id", "author_id", "authors", "id");
        assertThat(alter)
                .isEqualTo("ALTER TABLE books ADD CONSTRAINT fk_books_author_id FOREIGN KEY (author_id) REFERENCES authors (id)");
    }

    @Test
    void constraintNameIsDeterministic() {
        assertThat(AbstractSqlDialect.foreignKeyConstraintName("books", "author_id"))
                .isEqualTo("fk_books_author_id");
    }

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
