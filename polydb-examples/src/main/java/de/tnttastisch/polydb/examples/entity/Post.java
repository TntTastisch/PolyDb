package de.tnttastisch.polydb.examples.entity;

import de.tnttastisch.polydb.core.annotations.*;

import java.util.UUID;

/**
 * Example entity demonstrating the <em>owning</em> side of a one-to-many / many-to-one association.
 *
 * <p>A {@code Post} belongs to exactly one {@link User} (its author). Because the foreign key lives
 * on this entity's table, {@code Post} is the side that actually owns and writes the relation column.
 * It is the counterpart to {@link User#getPosts()}, which maps the inverse side.</p>
 *
 * <p>It also shows the basic entity wiring every PolyDB entity needs: {@link Entity} to register the
 * class, {@link Table} to pick the table name, {@link Id} to mark the primary key, and {@link Column}
 * to configure individual columns.</p>
 */
@Entity
@Table(name = "posts")
public class Post {

    /** Primary key. {@link Id} marks the identity column PolyDB uses for load/save/delete. */
    @Id
    @Column(name = "id")
    private UUID id;

    /** A plain scalar column; {@code length = 200} caps the generated VARCHAR width. */
    @Column(name = "title", length = 200)
    private String title;

    /**
     * Owning side of the relation: the {@code author_id} foreign-key column on the {@code posts}
     * table references {@code users.id}. {@link JoinColumn} names that foreign-key column, and
     * because {@link ManyToOne} defaults to eager fetching, the {@code author} is loaded
     * automatically whenever a {@code Post} is read. {@code optional = false} marks the
     * association as mandatory (every post must have an author).
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
