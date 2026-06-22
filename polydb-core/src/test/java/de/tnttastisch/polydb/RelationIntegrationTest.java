package de.tnttastisch.polydb;

import de.tnttastisch.polydb.query.Repository;
import de.tnttastisch.polydb.testentities.Author;
import de.tnttastisch.polydb.testentities.Book;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationIntegrationTest {

    private static final String ENTITY_PACKAGE = "de.tnttastisch.polydb.testentities";

    private PolyDB start(String name) {
        return PolyDB.builder()
                .url("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage(ENTITY_PACKAGE)
                .autoMigration(true)
                .start();
    }

    @Test
    void persistsRelatedEntitiesViaCascadeAndLoadsEagerRelationOnRead() {
        try (PolyDB db = start("rel_" + UUID.randomUUID().toString().replace("-", ""))) {
            Repository<Author> authors = db.repository(Author.class);
            Repository<Book> books = db.repository(Book.class);

            Author author = new Author(UUID.randomUUID(), "Jane");
            author.addBook(new Book(UUID.randomUUID(), "First", author));
            author.addBook(new Book(UUID.randomUUID(), "Second", author));

            // Cascade.PERSIST: saving the author also persists both books with the foreign key set.
            authors.save(author);

            List<Book> allBooks = books.findAll();
            assertThat(allBooks).hasSize(2);

            // Reading a book populates the EAGER @ManyToOne author.
            Book reread = books.findById(allBooks.get(0).getId()).orElseThrow();
            assertThat(reread.getAuthor()).isNotNull();
            assertThat(reread.getAuthor().getName()).isEqualTo("Jane");
        }
    }

    @Test
    void enforcesForeignKeyWhenReferencedRowIsMissing() {
        try (PolyDB db = start("fk_" + UUID.randomUUID().toString().replace("-", ""))) {
            Repository<Book> books = db.repository(Book.class);

            // The author is never saved (no cascade on the owning side) -> the foreign key is violated.
            Author ghost = new Author(UUID.randomUUID(), "Ghost");
            Book orphan = new Book(UUID.randomUUID(), "Orphan", ghost);

            assertThatThrownBy(() -> books.save(orphan)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void secondStartupDetectsNoSchemaChanges() {
        String name = "stable_" + UUID.randomUUID().toString().replace("-", "");
        // first run creates the schema
        start(name).close();
        // second run against the same in-memory database must be idempotent (tables + FKs already exist)
        try (PolyDB db = start(name)) {
            Repository<Author> authors = db.repository(Author.class);
            assertThat(authors.findAll()).isEmpty();
        }
    }
}
