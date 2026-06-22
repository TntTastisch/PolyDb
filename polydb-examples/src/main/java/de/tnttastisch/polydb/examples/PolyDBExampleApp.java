package de.tnttastisch.polydb.examples;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.examples.entity.Post;
import de.tnttastisch.polydb.examples.entity.User;
import de.tnttastisch.polydb.query.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

public class PolyDBExampleApp {

    private static final Logger log = LoggerFactory.getLogger(PolyDBExampleApp.class);

    public static void main(String[] args) {
        try (PolyDB polyDB = PolyDB.builder()
                .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.examples.entity")
                .autoMigration(true)
                .start()) {

            Repository<User> userRepository = polyDB.repository(User.class);
            Repository<Post> postRepository = polyDB.repository(Post.class);

            User user = new User();
            user.setId(UUID.randomUUID());
            user.setUsername("TntTastisch");
            user.setEmail("info@tnttastisch.de");
            user.setCreatedAt(LocalDateTime.now());

            // Cascade.PERSIST: saving the user also persists the post and wires up the foreign key.
            user.addPost(new Post(UUID.randomUUID(), "Hello PolyDB", user));
            userRepository.save(user);

            for (User u : userRepository.findAll()) {
                log.info("Found user: {} ({})", u.getUsername(), u.getEmail());
            }

            // The @ManyToOne author is eagerly loaded when reading a post.
            for (Post post : postRepository.findAll()) {
                log.info("Post '{}' by {}", post.getTitle(), post.getAuthor().getUsername());
            }
        } // close() is invoked automatically
    }
}
