package de.tnttastisch.polydb.examples.entity;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.JoinColumn;
import de.tnttastisch.polydb.core.annotations.ManyToOne;
import de.tnttastisch.polydb.core.annotations.Table;

import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "title", length = 200)
    private String title;

    /**
     * Owning side of the relation: the {@code author_id} foreign-key column on the {@code posts}
     * table references {@code users.id}. Eagerly loaded by default.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    public Post() {
    }

    public Post(UUID id, String title, User author) {
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

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }
}
