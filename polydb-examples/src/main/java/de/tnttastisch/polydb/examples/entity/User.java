package de.tnttastisch.polydb.examples.entity;

import de.tnttastisch.polydb.core.annotations.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Example entity demonstrating the <em>inverse</em> side of a one-to-many association together with
 * cascading persistence.
 *
 * <p>A {@code User} owns many {@link Post}s. The foreign key lives on the {@code posts} table (see
 * {@link Post#getAuthor()}), so {@code User} does not store a relation column itself; it merely maps
 * back to the owning side via {@code mappedBy}. This is the counterpart to {@link Post}'s
 * {@code @ManyToOne}.</p>
 *
 * <p>It additionally shows {@link Unique} (a unique constraint on {@code username}) and the typical
 * scalar columns such as {@code email} and {@code created_at}.</p>
 */
@Entity
@Table(name = "users")
public class User {

    /** Primary key. */
    @Id
    @Column(name = "id")
    private UUID id;

    /** Scalar column with both a length limit and a {@link Unique} constraint. */
    @Column(name = "username", length = 50)
    @Unique
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Inverse side of the relation: there is no column on the {@code users} table for this field.
     * {@code mappedBy = "author"} points at the {@code author} field on {@link Post}, which owns the
     * foreign key. {@link CascadeType#PERSIST} means saving a {@code User} also persists every
     * {@code Post} in this list and wires up their {@code author_id}, so callers only need to save
     * the user (see {@code PolyDBExampleApp}).
     */
    @OneToMany(mappedBy = "author", cascade = CascadeType.PERSIST)
    private List<Post> posts = new ArrayList<>();

    public User() {
    }

    public User(UUID id, String username, String email, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Post> getPosts() {
        return posts;
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts;
    }

    /**
     * Convenience helper that keeps both sides of the bidirectional relation consistent: it sets
     * this user as the post's author (the owning side that holds the foreign key) and adds the post
     * to this user's collection (the inverse side). Always updating both ends avoids surprises after
     * a save/reload cycle.
     */
    public void addPost(Post post) {
        post.setAuthor(this);
        this.posts.add(post);
    }
}
