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

class SchemaComparatorTest {

    private final EntityParser parser = new EntityParser();
    private final SchemaComparator comparator = new SchemaComparator();

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

    private TableSchema existingAuthorsTable() {
        TableSchema authors = new TableSchema("authors");
        authors.addColumn(new ColumnSchema("id", Types.OTHER, "UUID", 16, false, false));
        authors.addColumn(new ColumnSchema("name", Types.VARCHAR, "VARCHAR", 100, true, false));
        return authors;
    }

    private TableSchema existingBooksTableWithoutForeignKey() {
        TableSchema books = new TableSchema("books");
        books.addColumn(new ColumnSchema("id", Types.OTHER, "UUID", 16, false, false));
        books.addColumn(new ColumnSchema("title", Types.VARCHAR, "VARCHAR", 200, true, false));
        books.addColumn(new ColumnSchema("author_id", Types.OTHER, "UUID", 16, false, false));
        return books;
    }

    @Entity
    @Table(name = "a")
    static class NodeA {
        @Id @Column(name = "id")
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "b_id")
        private NodeB b;
    }

    @Entity
    @Table(name = "b")
    static class NodeB {
        @Id @Column(name = "id")
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "a_id")
        private NodeA a;
    }

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
