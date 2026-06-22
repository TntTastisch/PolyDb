package de.tnttastisch.polydb.testentities;

import de.tnttastisch.polydb.core.annotations.CascadeType;
import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.OneToMany;
import de.tnttastisch.polydb.core.annotations.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test fixture modelling the inverse (non-owning) side of a one-to-many relation: an author owns a
 * collection of {@link Book}s mapped by {@code Book.author}. The relation is {@code LAZY}
 * by default and cascades {@link CascadeType#PERSIST}, so saving an author also persists its books.
 *
 * <p>Used by the relation and lifecycle integration tests as well as the parser, dialect and schema
 * comparison tests as the "parent"/referenced entity of the author &harr; book relationship.</p>
 */
@Entity
@Table(name = "authors")
public class Author {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", length = 100)
    private String name;

    @OneToMany(mappedBy = "author", cascade = CascadeType.PERSIST)
    private List<Book> books = new ArrayList<>();

    public Author() {
    }

    public Author(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Book> getBooks() {
        return books;
    }

    public void setBooks(List<Book> books) {
        this.books = books;
    }

    public void addBook(Book book) {
        book.setAuthor(this);
        this.books.add(book);
    }
}
