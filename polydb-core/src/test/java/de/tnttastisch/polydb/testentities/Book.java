package de.tnttastisch.polydb.testentities;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.JoinColumn;
import de.tnttastisch.polydb.core.annotations.ManyToOne;
import de.tnttastisch.polydb.core.annotations.Table;

import java.util.UUID;

/**
 * Test fixture modelling the owning side of a many-to-one relation: each book references its
 * {@link Author} through the non-null {@code author_id} foreign key
 * ({@code @ManyToOne(optional = false)} + {@code @JoinColumn(nullable = false)}), which is eagerly
 * fetched on read.
 *
 * <p>Acts as the "child"/referencing entity throughout the test suite: it drives foreign-key DDL
 * generation in the dialect tests, the owning {@code MANY_TO_ONE} relation in the parser tests, and
 * the cascade-persist / eager-load / foreign-key-violation scenarios in the integration tests.</p>
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "title", length = 200)
    private String title;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    public Book() {
    }

    public Book(UUID id, String title, Author author) {
        this.id = id;
        this.title = title;
        this.author = author;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }
}
