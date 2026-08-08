package de.tnttastisch.polydb.schema.comparison;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.JoinColumn;
import de.tnttastisch.polydb.core.annotations.ManyToOne;
import de.tnttastisch.polydb.core.annotations.Table;
import de.tnttastisch.polydb.migration.operation.AddForeignKeyOperation;
import de.tnttastisch.polydb.migration.operation.CreateTableOperation;
import de.tnttastisch.polydb.migration.operation.MigrationOperation;
import de.tnttastisch.polydb.schema.db.ColumnSchema;
import de.tnttastisch.polydb.schema.db.DatabaseSchema;
import de.tnttastisch.polydb.schema.db.TableSchema;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link SchemaComparator}: it diffs the desired entity models against the current
 * {@link DatabaseSchema} and emits ordered {@link MigrationOperation}s. Focus areas are dependency-aware
 * table ordering, when foreign keys are inlined into {@code CreateTableOperation} versus deferred to a
 * separate {@code AddForeignKeyOperation}, and adding missing constraints to pre-existing tables. Uses
 * the {@code Author}/{@code Book} fixtures for the acyclic case and inline {@code NodeA/B/C} fixtures to
 * construct a dependency cycle.
 */
class SchemaComparatorTest {

    private final EntityParser parser = new EntityParser();
    private final SchemaComparator comparator = new SchemaComparator();

    @Test
    void createsReferencedTableBeforeReferencingTableWithInlineForeignKey() {
        EntityModel author = parser.parseEntity(de.tnttastisch.polydb.testentities.Author.class);
        EntityModel book = parser.parseEntity(de.tnttastisch.polydb.testentities.Book.class);

        // Declared "wrong" order on purpose: the comparator must reorder by dependency.
        List<MigrationOperation> changes = comparator.compare(List.of(book, author), new DatabaseSchema());

        List<String> createdTables = changes.stream()
                .filter(c -> c instanceof CreateTableOperation)
                .map(c -> ((CreateTableOperation) c).tableName())
                .toList();
        assertThat(createdTables).containsExactly("authors", "books");

        CreateTableOperation booksCreate = changes.stream()
                .filter(c -> c instanceof CreateTableOperation)
                .map(c -> (CreateTableOperation) c)
                .filter(c -> c.tableName().equals("books"))
                .findFirst()
                .orElseThrow();
        assertThat(booksCreate.inlineForeignKeys()).hasSize(1);

        // Acyclic graph -> foreign keys are inlined, none deferred to ALTER.
        assertThat(changes).noneMatch(c -> c instanceof AddForeignKeyOperation);
    }

    @Test
    void addsForeignKeyToExistingTableMissingTheConstraint() {
        EntityModel author = parser.parseEntity(de.tnttastisch.polydb.testentities.Author.class);
        EntityModel book = parser.parseEntity(de.tnttastisch.polydb.testentities.Book.class);

        DatabaseSchema dbSchema = new DatabaseSchema();
        dbSchema.addTable(existingAuthorsTable());
        dbSchema.addTable(existingBooksTableWithoutForeignKey());

        List<MigrationOperation> changes = comparator.compare(List.of(book, author), dbSchema);

        assertThat(changes).noneMatch(c -> c instanceof CreateTableOperation);
        AddForeignKeyOperation fk = changes.stream()
                .filter(c -> c instanceof AddForeignKeyOperation)
                .map(c -> (AddForeignKeyOperation) c)
                .findFirst()
                .orElseThrow();
        assertThat(fk.tableName()).isEqualTo("books");
        assertThat(fk.column()).isEqualTo("author_id");
        assertThat(fk.referencedTable()).isEqualTo("authors");
        assertThat(fk.constraintName()).isEqualTo("fk_books_author_id");
    }

    @Test
    void cyclicDependencyDefersOnlyTheCycleClosingForeignKeyAndInlinesAcyclicOnes() {
        EntityModel a = parser.parseEntity(NodeA.class);
        EntityModel b = parser.parseEntity(NodeB.class);
        EntityModel c = parser.parseEntity(NodeC.class);

        List<MigrationOperation> changes = comparator.compare(List.of(c, a, b), new DatabaseSchema());

        List<String> createOrder = changes.stream()
                .filter(ch -> ch instanceof CreateTableOperation)
                .map(ch -> ((CreateTableOperation) ch).tableName())
                .toList();
        // The referenced table "a" must be created before its acyclic dependent "c".
        assertThat(createOrder.indexOf("a")).isLessThan(createOrder.indexOf("c"));

        // The acyclic c -> a foreign key is inlined, not deferred.
        CreateTableOperation cCreate = changes.stream()
                .filter(ch -> ch instanceof CreateTableOperation)
                .map(ch -> (CreateTableOperation) ch)
                .filter(ch -> ch.tableName().equals("c"))
                .findFirst()
                .orElseThrow();
        assertThat(cCreate.inlineForeignKeys()).hasSize(1);

        // Exactly one foreign key is deferred to ALTER: the one that closes the a <-> b cycle.
        List<AddForeignKeyOperation> deferred = changes.stream()
                .filter(ch -> ch instanceof AddForeignKeyOperation)
                .map(ch -> (AddForeignKeyOperation) ch)
                .toList();
        assertThat(deferred).hasSize(1);
        assertThat(deferred.get(0).column()).isIn("a_id", "b_id");
    }

    /** Builds the current-schema representation of an already-migrated {@code authors} table. */
    private TableSchema existingAuthorsTable() {
        TableSchema authors = new TableSchema("authors");
        authors.addColumn(new ColumnSchema("id", Types.OTHER, "UUID", 16, false, false));
        authors.addColumn(new ColumnSchema("name", Types.VARCHAR, "VARCHAR", 100, true, false));
        return authors;
    }

    /** Builds a current {@code books} table that has the {@code author_id} column but no FK constraint. */
    private TableSchema existingBooksTableWithoutForeignKey() {
        TableSchema books = new TableSchema("books");
        books.addColumn(new ColumnSchema("id", Types.OTHER, "UUID", 16, false, false));
        books.addColumn(new ColumnSchema("title", Types.VARCHAR, "VARCHAR", 200, true, false));
        books.addColumn(new ColumnSchema("author_id", Types.OTHER, "UUID", 16, false, false));
        return books;
    }

    /** Cycle fixture: {@code a} references {@code b}, forming the {@code a <-> b} cycle with {@link NodeB}. */
    @Entity
    @Table(name = "a")
    static class NodeA {
        @Id @Column(name = "id")
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "b_id")
        private NodeB b;
    }

    /** Cycle fixture: {@code b} references {@code a}, closing the {@code a <-> b} cycle. */
    @Entity
    @Table(name = "b")
    static class NodeB {
        @Id @Column(name = "id")
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "a_id")
        private NodeA a;
    }

    /** Acyclic fixture: {@code c} references {@code a} only, so its foreign key can always be inlined. */
    @Entity
    @Table(name = "c")
    static class NodeC {
        @Id @Column(name = "id")
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "a_id")
        private NodeA a;
    }
}
