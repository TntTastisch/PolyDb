package de.tnttastisch.polydb;

import de.tnttastisch.polydb.query.Repository;
import de.tnttastisch.polydb.testentities.Author;
import de.tnttastisch.polydb.testentities.Book;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end relation behaviour against a real (in-memory H2) database, covering the full
 * parse &rarr; migrate &rarr; persist &rarr; query path through the {@link PolyDB} facade and its
 * {@link Repository} instances. Exercises the {@link Author}/{@link Book} one-to-many / many-to-one
 * pair: cascade-persist, eager loading of the owning side, foreign-key enforcement and idempotent
 * re-migration. Every test runs on its own uniquely named database with auto-migration enabled.
 */
class RelationIntegrationTest {

    private static final String ENTITY_PACKAGE = "de.tnttastisch.polydb.testentities";

    /** Starts an isolated in-memory H2-backed PolyDB instance for the database with the given name. */
    private PolyDB start(String name) {
        return PolyDB.builder()
                .url("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage(ENTITY_PACKAGE)
                .autoMigration(true)
                .start();
    }

    /**
     * Saving an author with two attached books persists the children via {@code CascadeType.PERSIST}
     * with their {@code author_id} foreign key populated, and re-reading a book then eagerly resolves
     * its owning {@code @ManyToOne} author back from the database.
     */
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

    /**
     * Saving a book whose author was never persisted (the owning many-to-one side does not cascade)
     * leaves a dangling foreign key, so the database rejects the insert with a runtime exception.
     */
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

    /**
     * Auto-migration is idempotent: starting against an already-migrated database (tables and foreign
     * keys present) detects no schema changes and succeeds, leaving the existing (empty) data intact.
     */
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
