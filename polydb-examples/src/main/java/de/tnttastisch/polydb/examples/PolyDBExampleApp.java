package de.tnttastisch.polydb.examples;

import de.tnttastisch.polydb.boot.PolyDB;
import de.tnttastisch.polydb.examples.entity.User;
import de.tnttastisch.polydb.query.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PolyDBExampleApp {

    private static final Logger log = LoggerFactory.getLogger(PolyDBExampleApp.class);

    public static void main(String[] args) {
        PolyDB polyDB = PolyDB.builder()
                .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.examples.entity")
                .autoMigration(true)
                .start();

        Repository<User> userRepository = polyDB.repository(User.class);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("TntTastisch");
        user.setEmail("info@tnttastisch.de");
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        List<User> users = userRepository.findAll();

        for (User u : users) {
            log.info("Found user: {} ({})", u.getUsername(), u.getEmail());
        }
    }
}
