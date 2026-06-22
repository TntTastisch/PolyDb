package de.tnttastisch.polydb.schema.comparison;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.JoinColumn;
import de.tnttastisch.polydb.core.annotations.ManyToOne;
import de.tnttastisch.polydb.core.annotations.Table;
import de.tnttastisch.polydb.schema.db.ColumnSchema;
import de.tnttastisch.polydb.schema.db.DatabaseSchema;
import de.tnttastisch.polydb.schema.db.TableSchema;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;
import de.tnttastisch.polydb.testentities.Author;
import de.tnttastisch.polydb.testentities.Book;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link SchemaComparator}: it diffs the desired entity models against the current
 * {@link DatabaseSchema} and emits ordered {@link SchemaChange}s. Focus areas are dependency-aware
 * table ordering, when foreign keys are inlined into {@code CREATE TABLE} versus deferred to a
 * separate {@code ADD FOREIGN KEY}, and adding missing constraints to pre-existing tables. Uses the
 * {@link Author}/{@link Book} fixtures for the acyclic case and inline {@code NodeA/B/C} fixtures to
 * construct a dependency cycle.
 */
class SchemaComparatorTest {

    private final EntityParser parser = new EntityParser();
    private final SchemaComparator comparator = new SchemaComparator();

    /**
     * Against an empty database the comparator orders {@code CREATE TABLE}s by dependency (the
     * referenced {@code authors} before the referencing {@code books}) even when the entities are
     * supplied in the wrong order, and because the graph is acyclic it inlines the foreign key rather
     * than deferring any to {@code ADD FOREIGN KEY}.
     */
    @Test
    void createsReferencedTableBeforeReferencingTableWithInlineForeignKey() {
        EntityModel author = parser.parseEntity(Author.class);
        EntityModel book = parser.parseEntity(Book.class);

        // Declared "wrong" order on purpose: the comparator must reorder by dependency.
        List<SchemaChange> changes = comparator.compare(List.of(book, author), new DatabaseSchema());

        List<String> createdTables = changes.stream()
                .filter(c -> c instanceof SchemaChange.CreateTable)
                .map(c -> ((SchemaChange.CreateTable) c).getEntity().getTableName())
                .toList();
        assertThat(createdTables).containsExactly("authors", "books");

        SchemaChange.CreateTable booksCreate = changes.stream()
                .filter(c -> c instanceof SchemaChange.CreateTable)
                .map(c -> (SchemaChange.CreateTable) c)
                .filter(c -> c.getEntity().getTableName().equals("books"))
                .findFirst()
                .orElseThrow();
        assertThat(booksCreate.getInlineForeignKeys()).hasSize(1);

        // Acyclic graph -> foreign keys are inlined, none deferred to ALTER.
        assertThat(changes).noneMatch(c -> c instanceof SchemaChange.AddForeignKey);
    }

    /**
     * When both tables already exist but the {@code books} table lacks its foreign key, the comparator
     * emits no {@code CREATE TABLE} and instead a single {@code ADD FOREIGN KEY} carrying the correct
     * table, column, referenced table and deterministic constraint name.
     */
    @Test
    void addsForeignKeyToExistingTableMissingTheConstraint() {
        EntityModel author = parser.parseEntity(Author.class);
        EntityModel book = parser.parseEntity(Book.class);

        DatabaseSchema dbSchema = new DatabaseSchema();
        dbSchema.addTable(existingAuthorsTable());
        dbSchema.addTable(existingBooksTableWithoutForeignKey());

        List<SchemaChange> changes = comparator.compare(List.of(book, author), dbSchema);

        assertThat(changes).noneMatch(c -> c instanceof SchemaChange.CreateTable);
        SchemaChange.AddForeignKey fk = changes.stream()
                .filter(c -> c instanceof SchemaChange.AddForeignKey)
                .map(c -> (SchemaChange.AddForeignKey) c)
                .findFirst()
                .orElseThrow();
        assertThat(fk.getTableName()).isEqualTo("books");
        assertThat(fk.getColumn()).isEqualTo("author_id");
        assertThat(fk.getReferencedTable()).isEqualTo("authors");
        assertThat(fk.getConstraintName()).isEqualTo("fk_books_author_id");
    }

    /**
     * With a dependency cycle ({@code a <-> b}) plus an acyclic edge ({@code c -> a}), the comparator
     * still orders {@code a} before its acyclic dependent {@code c} and inlines the satisfiable
     * {@code c -> a} foreign key, while deferring exactly one foreign key (the one that closes the
     * {@code a <-> b} cycle) to a separate {@code ADD FOREIGN KEY}.
     */
    @Test
    void cyclicDependencyDefersOnlyTheCycleClosingForeignKeyAndInlinesAcyclicOnes() {
        // a <-> b form a cycle; c -> a is acyclic. Declared order c, a, b deliberately puts the
        // acyclic dependent first to ensure ordering does not strand its (satisfiable) foreign key.
        EntityModel a = parser.parseEntity(NodeA.class);
        EntityModel b = parser.parseEntity(NodeB.class);
        EntityModel c = parser.parseEntity(NodeC.class);

        List<SchemaChange> changes = comparator.compare(List.of(c, a, b), new DatabaseSchema());

        List<String> createOrder = changes.stream()
                .filter(ch -> ch instanceof SchemaChange.CreateTable)
                .map(ch -> ((SchemaChange.CreateTable) ch).getEntity().getTableName())
                .toList();
        // The referenced table "a" must be created before its acyclic dependent "c".
        assertThat(createOrder.indexOf("a")).isLessThan(createOrder.indexOf("c"));

        // The acyclic c -> a foreign key is inlined, not deferred.
        SchemaChange.CreateTable cCreate = changes.stream()
                .filter(ch -> ch instanceof SchemaChange.CreateTable)
                .map(ch -> (SchemaChange.CreateTable) ch)
                .filter(ch -> ch.getEntity().getTableName().equals("c"))
                .findFirst()
                .orElseThrow();
        assertThat(cCreate.getInlineForeignKeys()).hasSize(1);

        // Exactly one foreign key is deferred to ALTER: the one that closes the a <-> b cycle.
        List<SchemaChange.AddForeignKey> deferred = changes.stream()
                .filter(ch -> ch instanceof SchemaChange.AddForeignKey)
                .map(ch -> (SchemaChange.AddForeignKey) ch)
                .toList();
        assertThat(deferred).hasSize(1);
        assertThat(deferred.get(0).getColumn()).isIn("a_id", "b_id");
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
