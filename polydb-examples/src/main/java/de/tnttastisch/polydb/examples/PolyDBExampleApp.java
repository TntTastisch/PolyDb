package de.tnttastisch.polydb.examples;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.examples.entity.Post;
import de.tnttastisch.polydb.examples.entity.User;
import de.tnttastisch.polydb.query.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * End-to-end example application that walks through the full PolyDB lifecycle:
 * boot the engine, obtain repositories, persist a small object graph with a relation, and read it
 * back. It ties together the {@link User} and {@link Post} entities and the
 * {@code V1_InitialDataMigration} migration found in this module.
 *
 * <p>The example deliberately uses an in-memory H2 database so it can be run as-is without any
 * external setup.</p>
 */
public class PolyDBExampleApp {

    private static final Logger log = LoggerFactory.getLogger(PolyDBExampleApp.class);

    public static void main(String[] args) {
        // 1. Boot PolyDB via its builder. PolyDB implements AutoCloseable, so a try-with-resources
        //    block guarantees the engine (and its connection pool) is shut down at the end.
        try (PolyDB polyDB = PolyDB.builder()
                .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1") // in-memory H2, kept alive for the JVM
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.examples.entity") // scanned for @Entity classes
                .autoMigration(true) // discover and apply migrations (e.g. V1_InitialDataMigration) on start
                .start()) {         // start() generates the schema, runs migrations, and returns the engine

            // 2. Obtain a type-safe repository per entity. Each gives basic CRUD plus relation handling.
            Repository<User> userRepository = polyDB.repository(User.class);
            Repository<Post> postRepository = polyDB.repository(Post.class);

            // 3. Build a fresh User instance in memory (not yet persisted).
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setUsername("TntTastisch");
            user.setEmail("info@tnttastisch.de");
            user.setCreatedAt(LocalDateTime.now());

            // 4. Attach a Post to the user. addPost() links both sides of the relation.
            //    Because User.posts is mapped with Cascade.PERSIST, saving the user also persists the
            //    post and wires up its author_id foreign key - no separate postRepository.save() needed.
            user.addPost(new Post(UUID.randomUUID(), "Hello PolyDB", user));
            userRepository.save(user);

            // 5. Read all users back from the database.
            for (User u : userRepository.findAll()) {
                log.info("Found user: {} ({})", u.getUsername(), u.getEmail());
            }

            // 6. Read all posts. The @ManyToOne author is eagerly loaded, so getAuthor() is populated
            //    without an extra query in the application code.
            for (Post post : postRepository.findAll()) {
                log.info("Post '{}' by {}", post.getTitle(), post.getAuthor().getUsername());
            }
        } // 7. close() is invoked automatically by try-with-resources, shutting PolyDB down cleanly.
    }
}
