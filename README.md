# PolyDb

PolyDb is a lightweight Java persistence framework for working with databases using annotations, automatic schema
generation, repository-based queries, and migrations.
It is designed to keep the setup simple while still providing a structured and extensible way to map Java entities to
database tables.

---

## Features

- Annotation-based entity mapping
- Automatic schema generation
- Repository layer interface-driven CRUD, derived query methods, custom `@Query` methods, pagination & sorting, specifications, projections, transactions, auditing, optimistic locking, and soft delete.
- Database dialect support
- Declarative, versioned Java migrations with dry-run and history
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

Repositories follow a Spring Data&ndash;style interface hierarchy &mdash; declare an interface and
PolyDb synthesises the implementation at runtime via a dynamic proxy (no implementation class needed):

| Interface | Adds |
|---|---|
| `Repository<T, ID>` | Marker only &mdash; carries the entity type and id type. |
| `CrudRepository<T, ID>` | Standard create/read/update/delete. |
| `PagingAndSortingRepository<T, ID>` | `findAll(Sort)`, `findAll(Pageable)`. |
| `SpecificationExecutor<T>` | `findAll` / `findOne` / `count` / `exists(Specification)`. |

The interfaces compose, so one repository can extend several:

```java
public interface UserRepository
        extends PagingAndSortingRepository<User, UUID>, SpecificationExecutor<User> {

    Optional<User> findByUsername(String username);   // derived query (see below)
}

UserRepository users = polyDB.getRepository(UserRepository.class);
```

`default` methods on your interface run as written, so you can compose reusable helpers without an
implementation class. The sections below summarise each capability; for the complete reference see
**[REPOSITORIES.md](REPOSITORIES.md)**.

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

## Derived Query Methods

Declare a method on your repository interface and PolyDb generates the SQL from its **name** &mdash; no
body, no `@Query`:

```java
public interface UserRepository extends CrudRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    List<User>     findByEnabledTrueOrderByCreatedAtDesc();
    List<User>     findByAgeGreaterThanAndUsernameContainingIgnoreCase(int age, String part);
    List<User>     findByRoleIn(Collection<Role> roles);
    long           countByEnabledTrue();
    boolean        existsByEmail(String email);
    long           deleteByEnabledFalse();
}
```

**Actions** (the method-name prefix): `findBy` / `readBy` / `getBy` / `queryBy` / `searchBy` (select),
`countBy` (long/int), `existsBy` (boolean), `deleteBy` / `removeBy` (void or deleted-row count).

**Return types** for finders: `List<T>`, `Optional<T>`, or a single `T`.

**Operators** (keywords appended to a property):

| Category      | Keywords |
|---------------|----------|
| Comparison    | *(none = equals)*, `Is`, `Equals`, `Not`, `LessThan`, `LessThanEqual`, `GreaterThan`, `GreaterThanEqual`, `Between`, `Before`, `After` |
| Strings       | `Like`, `NotLike`, `StartingWith`, `EndingWith`, `Containing`, `IgnoreCase` |
| Collections   | `In`, `NotIn` |
| Null / boolean| `IsNull`, `IsNotNull`, `True`, `False` |
| Combination   | `And`, `Or` |
| Sorting       | `OrderBy<Property>[Asc\|Desc]` (repeatable) |
| Limiting      | `findFirst...`, `findTop<N>...` |

Predicates over an owning relation are matched by their foreign key, accepting either the associated
entity or its id (e.g. `findByAuthor(User author)` or `findByAuthor(authorId)`). Enum properties are
compared by name. `And`/`Or`/direction keywords are recognised only at CamelCase boundaries, so
property names like `order` or `description` are not mis-split; a property whose name literally embeds
a keyword should use `@Query` instead.

---

## Sorting and Pagination

Extend `PagingAndSortingRepository` for `findAll(Sort)` and `findAll(Pageable)`:

```java
List<User> byName = users.findAll(Sort.by("username"));
List<User> newest = users.findAll(Sort.by(Direction.DESC, "createdAt"));

Page<User> page = users.findAll(PageRequest.of(0, 20, Sort.by("username")));
page.getTotalElements();  page.getTotalPages();  page.hasNext();
```

Derived methods can take a trailing `Sort` or `Pageable` and return `Page<T>` (counted),
`Slice<T>` (uncounted, one extra-row lookahead) or `List<T>`:

```java
Page<User>  findByEnabledTrue(Pageable pageable);
Slice<User> findByRole(Role role, Pageable pageable);
List<User>  findByRole(Role role, Sort sort);
```

Paging falls back to primary-key order when unsorted. The row-limiting SQL is dialect-aware
(`LIMIT`/`OFFSET`, or `OFFSET … FETCH` on SQL Server / DB2 / Firebird).

---

## Custom SQL: `@Query` and `@Modifying`

Bind a method to explicit **native SQL** (there is no JPQL layer):

```java
@Query("SELECT * FROM users WHERE age > :min ORDER BY age")
List<User> olderThan(@Param("min") int min);

@Query("SELECT COUNT(*) FROM users WHERE enabled = true")
long countEnabled();

@Modifying
@Query("UPDATE users SET enabled = false WHERE last_login < ?1")
int deactivateIdle(Instant cutoff);
```

Bind markers: named `:name` (with `@Param`), positional `?1`, or sequential `?` (a PostgreSQL `::`
cast is left untouched). Reads map to the entity, a scalar, a `List` of scalars, or a projection.
`@Modifying` marks writes and returns the affected-row count or `void`.

---

## Specifications

Composable, dynamic filters &mdash; PolyDb's take on JPA Criteria. Extend `SpecificationExecutor<T>`:

```java
Specification<User> adults  = root -> root.greaterThanOrEqual("age", 18);
Specification<User> enabled = root -> root.isTrue("enabled");

List<User> result = users.findAll(adults.and(enabled));
Page<User> paged  = users.findAll(adults.or(enabled), PageRequest.of(0, 20));
long total        = users.count(adults);
```

`Root` builds predicates over properties (`equal`, comparisons, `like`/`contains`/`startsWith`, `in`,
`between`, null/boolean checks); combine with `and`/`or`/`not`/`allOf`/`anyOf`. A `null` specification
(or operand) means "no restriction".

---

## Projections

Return an interface or a record exposing a subset instead of the full entity:

```java
public interface UserView { String getUsername(); Role getRole(); }
public record UserSummary(String username, String email) {}

List<UserView>        findByEnabledTrue();              // derived
Optional<UserSummary> findByUsername(String username);  // derived

@Query("SELECT username, email FROM users WHERE age > :min")
List<UserSummary> summaries(@Param("min") int min);     // @Query, column-reduced
```

Derived-query projections map from the loaded entity (property names match entity properties);
`@Query` projections map from the selected columns (property names match the column labels).

---

## Transactions

Run a unit of work atomically &mdash; cascades and bulk writes commit or roll back together:

```java
TransactionTemplate tx = new TransactionTemplate(polyDB.getDataSource());

tx.executeWithoutResult(() -> {
    accounts.save(from);
    accounts.save(to);          // both, or neither
});

BigDecimal balance = tx.execute(() -> accounts.findById(id).orElseThrow().getBalance());
tx.executeReadOnly(() -> accounts.count());
```

Nesting joins the outer transaction (an outer rollback also undoes inner writes).

---

## Auditing, Versioning &amp; Soft Delete

Field annotations, applied automatically on save/delete/read:

```java
@CreatedDate       Instant createdAt;   // set on insert
@LastModifiedDate  Instant updatedAt;   // set on insert and update
@CreatedBy         String  createdBy;   // from AuditingContext
@LastModifiedBy    String  updatedBy;
@Version           long    version;     // optimistic locking
@SoftDelete        boolean deleted;     // delete flags the row; reads hide flagged rows
```

```java
AuditingContext.setAuditorProvider(() -> currentUser());   // for @CreatedBy / @LastModifiedBy

EntityEvents.addListener(new EntityListener() {
    @Override public void afterSave(Object entity)   { /* ... */ }
    @Override public void afterDelete(Object entity) { /* ... */ }
});
```

A concurrent update of a `@Version` entity raises `OptimisticLockException`. A `@SoftDelete` flag makes
`delete` set the flag and every read filter it out (the row stays in the table). Domain events fire
after save and delete.

---

## Migrations

Automatic schema sync and versioned migrations share **one internal engine**. Both produce a list of
dialect-independent `MigrationOperation`s (create table, add column, add foreign key, insert data, …)
which a single `MigrationExecutor` renders to SQL through the active `Dialect` and applies. You never
have to write dialect-specific SQL by hand.

Migrations are scanned automatically from the `.migrations` sub-package of your entity package and
applied once each, in ascending version order, tracked in the `polydb_schema_history` table. Versions
are compared **as strings**, so zero-pad (`"001"`) or use sortable timestamps (`"20260808_1200"`) —
`"10"` sorts before `"9"`.

### Declarative migrations (recommended)

Extend `BaseMigration` and describe the change in `up(...)` with the fluent `MigrationBuilder`:

```java
import de.tnttastisch.polydb.migration.core.BaseMigration;
import de.tnttastisch.polydb.migration.plan.MigrationBuilder;

import static de.tnttastisch.polydb.migration.plan.MigrationBuilder.row;
import static de.tnttastisch.polydb.migration.precondition.Preconditions.ifTableMissing;

public class V2_UserProfiles extends BaseMigration {

    public String getVersion()     { return "20260808_1200"; }
    public String getDescription() { return "profiles table + default roles"; }

    @Override
    public void up(MigrationBuilder m) {
        m.preconditions(ifTableMissing("profiles"));          // robust against legacy databases

        m.createTable("profiles", t -> {
            t.uuidPrimaryKey("id");                           // reusable column helpers
            t.column("user_id", java.util.UUID.class).notNull();
            t.string("bio", 500).nullable();
            t.timestamps();                                   // created_at / updated_at
            t.softDelete();                                   // deleted_at
        });
        m.addForeignKey("profiles", "user_id", "users", "id");
        m.createIndex("profiles", "user_id").unique();

        m.seed("roles")                                       // declarative seed data
         .insert(row("id", 1, "name", "ADMIN"))
         .upsert("id", row("id", 2, "name", "USER"));
    }
    // down() is optional — reversible operations (create table/column/index, add FK) roll back
    // automatically. Override down(MigrationBuilder) only for irreversible steps.
}
```

Available builder operations include `createTable` / `dropTable` / `renameTable`,
`addColumn` / `dropColumn` / `renameColumn` / `alterColumn`, `createIndex` / `dropIndex`,
`addForeignKey` / `dropForeignKey`, the `seed(...)` API (`insert` / `update` / `delete` / `upsert`),
column helpers (`timestamps()`, `softDelete()`, `auditColumns()`, `uuidPrimaryKey()`), and an
`sql(...)` escape hatch for anything the DSL cannot express.

### Rollback and transactions

Every reversible operation knows its inverse (`CreateTable → DropTable`, `AddColumn → DropColumn`, …).
Where the dialect supports **transactional DDL** (PostgreSQL, SQLite, SQL Server, Firebird, DB2) a
whole migration runs in one transaction and is rolled back on failure. Where it does not (H2, MySQL,
MariaDB, Oracle, which implicitly commit DDL) the engine applies operations one by one and, on failure,
runs each applied operation's reverse as best-effort compensation.

### Dry run and SQL preview

```java
PolyDB.builder()
    .url("jdbc:postgresql://…")
    .entityPackage("com.example.entity")
    .dryRun(true)   // compute plans and log the SQL — apply nothing
    .start();
```

`ExecutionMode.DRY_RUN` / `PREVIEW` compute the full plan and render its SQL without changing the
database; the resulting `SqlScript` can be written to a `.sql` file.

### Preconditions

`ifTableExists` / `ifTableMissing` / `ifColumnExists` / `ifColumnMissing` / `ifIndexExists` guard a
migration; if any is unmet the migration is skipped (not failed), which keeps it safe to run against
legacy databases.

### History metadata

`polydb_schema_history` records, per version, the description, class name, type (`MANUAL` / `LEGACY`),
a content **checksum**, the PolyDB version, execution time, status and any error message. The table is
upgraded in place (new columns added additively) so existing history is preserved.

### Legacy migrations

Migrations that implement `Migration` directly and write raw SQL via `MigrationContext` remain fully
supported (they run through the same executor, gaining history + metadata, but cannot be previewed or
dry-run):

```java
public class V0_Legacy implements Migration {
    public String getVersion()     { return "0"; }
    public String getDescription() { return "raw sql"; }
    public void migrate(MigrationContext context) throws Exception {
        try (var conn = context.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO users (id, username) VALUES ('…', 'SYSTEM')");
        }
    }
}
```

### Generating migrations from entity changes

`MigrationCodeGenerator` turns the schema comparator's plan (the diff between your entities and the
live database) into a `BaseMigration` source file, so production systems can run migrations only, with
automatic schema changes turned off (`autoMigration(false)`).

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
