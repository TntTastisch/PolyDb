# PolyDb

PolyDb is a lightweight Java persistence framework for working with databases using annotations, automatic schema
generation, repository-based queries, and migrations.
It is designed to keep the setup simple while still providing a structured and extensible way to map Java entities to
database tables.

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

## Requirements

- Java 17
- Maven
- A supported database
- A JDBC driver for your database

---

## Quick Start

### 1. Add an entity

```java
package de.tnttastisch.polydb.examples.entity;

import de.tnttastisch.polydb.core.annotations.*;

import java.time.OffsetDateTime;
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
    
    @Column(name = "is_active")
    private boolean isActive = false; // initialized by default

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

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
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
}
```

---

### 2. Start PolyDb

`PolyDB` implements `AutoCloseable`, so use try-with-resources to release the connection pool
automatically:

```java
try (PolyDB polyDB = PolyDB.builder()
        .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
        .username("sa")
        .password("")
        .entityPackage("de.tnttastisch.polydb.examples.entity")
        .autoMigration(true)
        .start()) {

    UserRepository userRepository = polyDB.getRepository(UserRepository.class);
    // ... use repositories here ...
} // close() is invoked automatically
```

---

### 3. Use a repository

Declare a repository interface for your entity &mdash; PolyDb implements it for you at runtime:

```java
public interface UserRepository extends CrudRepository<User, UUID> {
}
```

```java
UserRepository userRepository = polyDB.getRepository(UserRepository.class);

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

If you don't want to declare an interface, the quick path returns a generic repository straight from
the entity class (with the id typed as `Object`):

```java
CrudRepository<User, Object> userRepository = polyDB.repository(User.class);
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

Defines column metadata such as name, length, nullability, precision, scale, and the column default.

| Attribute | Default | Effect |
|-----------|---------|--------|
| `name` | field name (lower-cased) | Column name. |
| `nullable` | `true` | `false` emits a `NOT NULL` constraint. |
| `length` | `255` | Length for string columns. |
| `precision` / `scale` | `0` | Digits / decimal places for numeric columns (`0` = dialect default). |
| `defaultValue` | `""` | SQL `DEFAULT` for the column (see below). |

#### Column defaults

A column's `DEFAULT` clause is resolved in two ways:

1. **Explicit** &mdash; `@Column(defaultValue = "...")` is emitted **verbatim** as a SQL literal, so it
   must be valid for the target dialect: `"false"` / `"0"` for booleans/numbers, `"'active'"` (with the
   inner quotes) for strings, or expressions like `"CURRENT_TIMESTAMP"`.
2. **Derived** &mdash; when `defaultValue` is left empty, the default is taken from the field's
   initialised value on a freshly constructed instance. `private boolean isActive = false` yields
   `DEFAULT false` and `private String role = "user"` yields `DEFAULT 'user'`. Fields left at `null`
   (and types with no obvious literal form such as `UUID`, dates, or collections) get no `DEFAULT`;
   use the explicit attribute for those.

```java
@Column(name = "is_active", nullable = false)
private boolean isActive = false;          // -> is_active BOOLEAN DEFAULT false NOT NULL

@Column(name = "role", defaultValue = "'guest'")
private String role = "user";              // explicit wins -> role VARCHAR(255) DEFAULT 'guest'
```

Having a default &mdash; derived or explicit &mdash; is what makes it safe to add a `NOT NULL` column
to a table that **already holds rows**: the database backfills the existing rows with it instead of
rejecting the `ALTER TABLE ... ADD` for containing null values.

### `@Unique`

Marks a column as unique.

### `@Index`

Defines an index on a class or field.

### `@Transient`

Marks a field as non-persistent. The field becomes neither a column nor a relation (equivalent to
the Java `transient` keyword). `static` and synthetic fields are skipped as well.

---

## Relations

PolyDb maps associations between entities and realises them as foreign keys on the SQL dialects.

### Annotations

| Annotation | Side | Effect |
|------------|------|--------|
| `@ManyToOne` | owning | Adds a foreign-key column on this table. Default fetch `EAGER`. |
| `@OneToOne` | owning (`mappedBy` empty) / inverse | Owning side adds a foreign-key column. Default fetch `EAGER`. |
| `@OneToMany(mappedBy = "...")` | inverse | No column on this table; the foreign key lives on the target. Default fetch `LAZY`. |
| `@ManyToMany` | owning (`@JoinTable`) / inverse (`mappedBy`) | Realised through a join table. Default fetch `LAZY`. |
| `@JoinColumn` | — | Customises the foreign-key column (`name`, `referencedColumnName`, `nullable`). Defaults to `<field>_id`. |
| `@JoinTable` | — | Declares the join table for the owning side of a `@ManyToMany`. |

Fetch types live in `FetchType { LAZY, EAGER }` and cascading in
`CascadeType { PERSIST, MERGE, REMOVE, ALL }`.

### Example

```java
@Entity
@Table(name = "posts")
public class Post {

    @Id @Column(name = "id")
    private UUID id;

    @Column(name = "title")
    private String title;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;            // owning side -> author_id column + foreign key
}

@Entity
@Table(name = "users")
public class User {

    @Id @Column(name = "id")
    private UUID id;

    @OneToMany(mappedBy = "author", cascade = CascadeType.PERSIST)
    private List<Post> posts = new ArrayList<>();   // inverse side, no column
}
```

The foreign-key column type matches the target entity's `@Id` type (e.g. `UUID`, `BIGINT`), resolved
per dialect. Owning `@ManyToOne(optional = false)` / `@JoinColumn(nullable = false)` produce a
`NOT NULL` foreign-key column.

### Behaviour

- **Writing** &mdash; for an owning relation the foreign key is written from the associated entity's
  id. `Cascade.PERSIST`/`ALL` saves the associated entity (and `@OneToMany` children) automatically;
  `Cascade.REMOVE` deletes children when the parent is deleted.
- **Reading** &mdash; `EAGER` relations are resolved one level deep when the owning entity is read.
  `LAZY` relations are **not** auto-populated yet (no runtime proxies); they are reserved for a future
  deferred-loading implementation.
- **Schema** &mdash; referenced tables are created before referencing tables (topological ordering).
  Cyclic dependencies (mutually referencing tables) are broken by adding the closing foreign key via
  `ALTER TABLE ... ADD CONSTRAINT` after both tables exist. On existing tables, a missing foreign key
  is added via `ALTER TABLE` as well.

---

## Repository API

Repositories follow a Spring Data&ndash;style interface hierarchy. The root `Repository<T, ID>` is an
empty marker carrying the entity type and its id type; `CrudRepository<T, ID>` adds the standard
create/read/update/delete operations. You declare an interface and PolyDb synthesises the
implementation at runtime via a dynamic proxy &mdash; no implementation class needed:

```java
public interface UserRepository extends CrudRepository<User, UUID> {
}

UserRepository users = polyDB.getRepository(UserRepository.class);
```

`default` methods on your interface run as written, so you can compose reusable helpers without an
implementation class.

### `CrudRepository<T, ID>`

```java
public interface CrudRepository<T, ID> extends Repository<T, ID> {
    <S extends T> S       save(S entity);           // insert or update (upsert), cascades to relations
    <S extends T> List<S> saveAll(Iterable<S> entities);
    Optional<T>           findById(ID id);
    boolean               existsById(ID id);
    List<T>               findAll();
    List<T>               findAllById(Iterable<ID> ids);
    long                  count();
    void                  delete(T entity);
    void                  deleteById(ID id);
    void                  deleteAllById(Iterable<? extends ID> ids);
    void                  deleteAll(Iterable<? extends T> entities);
    void                  deleteAll();
}
```

### Example

```java
User saved = userRepository.save(user);        // returns the persisted entity
Optional<User> found = userRepository.findById(saved.getId());
long total = userRepository.count();
boolean present = userRepository.existsById(saved.getId());
userRepository.deleteById(saved.getId());
```

### Without a declared interface

When you don't need custom query methods, obtain a generic repository directly from the entity class.
The id type is `Object` on this path; declare an interface (as above) for a type-safe id.

```java
CrudRepository<User, Object> users = polyDB.repository(User.class);
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

### Foreign keys per dialect

- **H2, PostgreSQL, Oracle, SQL Server, Firebird, DB2** &mdash; standard named constraints, inline at
  `CREATE TABLE` and via `ALTER TABLE ... ADD CONSTRAINT`.
- **MySQL / MariaDB** &mdash; foreign keys require the InnoDB engine, so generated tables append
  `ENGINE=InnoDB`.
- **SQLite** &mdash; foreign keys can only be declared **inline** at table creation; SQLite cannot add
  them via `ALTER TABLE` (such changes are skipped with a warning). Enforcement is off by default, so
  PolyDb runs `PRAGMA foreign_keys = ON` on every pooled connection. Consequently, **cyclic** foreign
  keys (which require a deferred `ALTER`) are not supported on SQLite.
- **MongoDB / Cassandra** &mdash; these stores have no enforced foreign keys (the relation methods are
  no-ops). NoSQL repositories are not implemented yet; relations there are a **design/future** concern:
  in **MongoDB** model them by *embedding* nested documents or *referencing* a foreign `_id` (resolved
  with a second query or `$lookup`); in **Cassandra** by *denormalisation*, UDTs or collection columns
  (query-first). Referential integrity is not enforced in either case.

---

## Lifecycle

`PolyDB` implements `AutoCloseable`. Use try-with-resources, or call `close()` / `shutdown()`
explicitly.

```java
try (PolyDB polyDB = PolyDB.builder() /* ... */ .start()) {
    // ...
}

// or manually
PolyDB polyDB = PolyDB.builder() /* ... */ .start();
try {
    // ...
} finally {
    polyDB.shutdown();   // alias for close()
}
```

- `close()` and `shutdown()` close the connection pool (and any future native NoSQL client).
- `close()` is **idempotent**: calling it more than once is a no-op.
- After closing, `repository(...)` throws `IllegalStateException("PolyDB has been closed")`.

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
- Example module: `polydb-core`
- Main entry point: `PolyDB.builder()`


### Use in Maven
```xml
<repository>
  <id>tnttastisch-repo-releases</id>
  <name>TntTastisch Repository</name>
  <url>https://repo.tnttastisch.de/releases</url>
</repository>


<dependency>
<groupId>de.tnttastisch</groupId>
<artifactId>polydb-core</artifactId>
<version>${current_version}</version>
</dependency>
```

---

## Contributing

Contributions, issues, and suggestions are welcome.

If you add new features, please include:

- tests
- documentation
- example usage where appropriate
