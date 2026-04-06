# PolyDb

PolyDb is a lightweight Java persistence framework for working with databases using annotations, automatic schema generation, repository-based queries, and migrations.

It is designed to keep the setup simple while still providing a structured and extensible way to map Java entities to database tables.

**Project repository:** [https://repo.tnttastisch.de](https://repo.tnttastisch.de)

---

## Features

- Annotation-based entity mapping
- Automatic schema generation
- Repository-style CRUD access
- Database dialect support
- Java-based migrations
- Support for multiple databases
- Simple bootstrap API

---

## Modules

PolyDb is split into several modules:

- **polydb-core** Core annotations, configuration classes, and shared exceptions

- **polydb-boot** Framework entry point and startup orchestration

- **polydb-schema** Entity parsing, schema models, schema comparison, and schema reading

- **polydb-dialect** Database-specific SQL generation and type mapping

- **polydb-query** Repository implementation and query execution

- **polydb-migration** Migration scanning, execution, and history tracking

- **polydb-examples** Example application showing how to use PolyDb

---

## Requirements

- Java 11
- Maven
- A supported database
- A JDBC driver for your database

---

## Quick Start

### 1. Add an entity
```java
package de.tnttastisch.polydb.examples.entity;

import de.tnttastisch.polydb.core.annotations.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "username", length = 50)
    @Unique
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

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
}
```
---

### 2. Start PolyDb
```java
PolyDB polyDB = PolyDB.builder()
.url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
.username("sa")
.password("")
.entityPackage("de.tnttastisch.polydb.examples.entity")
.autoMigration(true)
.start();
```
---

### 3. Use a repository
```java
Repository<User> userRepository = polyDB.repository(User.class);

User user = new User();
user.setId(UUID.randomUUID());
user.setUsername("TntTastisch");
user.setEmail("info@tnttastisch.de");
user.setCreatedAt(LocalDateTime.now());

userRepository.save(user);

List<User> users = userRepository.findAll();

for (User u : users) {
System.out.println("Found user: " + u.getUsername() + " (" + u.getEmail() + ")");
}
```
---

## Example Application

The `polydb-examples` module contains a simple working example application that:

- starts PolyDb
- creates a repository
- saves a user
- reads users back from the database

This is a good starting point if you want to understand the framework structure quickly.

---

## Entity Annotations

### `@Entity`
Marks a class as a database entity.

### `@Table`
Defines the table name.

### `@Id`
Marks the primary key field.

### `@Column`
Defines column metadata such as name, length, nullability, precision, and scale.

### `@Unique`
Marks a column as unique.

### `@Index`
Defines an index on a class or field.

---

## Repository API

PolyDb provides a simple repository abstraction:
```java
public interface Repository<T> {
void save(T entity);
Optional<T> findById(Object id);
List<T> findAll();
void delete(T entity);
void deleteById(Object id);
}
```

### Example
```java
Optional<User> user = userRepository.findById(id);
userRepository.deleteById(id);
```
---

## Migrations

PolyDb supports Java-based migrations.

Example migration:
```java
package de.tnttastisch.polydb.examples.entity.migrations;

import de.tnttastisch.polydb.migration.core.Migration;
import de.tnttastisch.polydb.migration.core.MigrationContext;

import java.sql.Connection;
import java.sql.Statement;

public class V1_InitialDataMigration implements Migration {

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getDescription() {
        return "Inserts initial system user";
    }

    @Override
    public void migrate(MigrationContext context) throws Exception {
        try (Connection conn = context.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO users (id, username, email, created_at) VALUES ('00000000-0000-0000-0000-000000000000', 'SYSTEM', 'system@polydb.org', NOW())");
        }
    }
}
```
Migrations are scanned automatically from the `.migrations` package inside your entity package.

---

## Supported Dialects

PolyDb includes dialect support for:

- H2
- MySQL
- MariaDB
- PostgreSQL
- SQLite
- Oracle
- SQL Server
- Firebird
- DB2
- MongoDB
- Cassandra

---

## Build

To build the whole project:
```bash
mvn clean install
```
To run the example module:
```bash
mvn -pl polydb-examples -am exec:java
```
---

## Example Use Case

A typical PolyDb setup looks like this:

1. Define entities with annotations
2. Configure PolyDb with a database URL
3. Enable automatic schema sync
4. Run migrations
5. Use repositories for data access

---

## Project Links

- Repository: [https://repo.tnttastisch.de](https://repo.tnttastisch.de)
- Example module: `polydb-examples`
- Main entry point: `PolyDB.builder()`

---

## Contributing

Contributions, issues, and suggestions are welcome.

If you add new features, please include:
- tests
- documentation
- example usage where appropriate
```
